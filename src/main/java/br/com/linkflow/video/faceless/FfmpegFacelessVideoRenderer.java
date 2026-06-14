package br.com.linkflow.video.faceless;

import br.com.linkflow.client.StorageClient;
import br.com.linkflow.config.FacelessProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renderiza vídeos FACELESS com ffmpeg: fundo (imagem de produto com Ken Burns ou
 * gradiente de marca) + locução + legendas ASS burned-in, em 9:16 (1080x1920).
 *
 * <p>Trabalha num diretório temporário e o remove no {@code finally} — o disco do
 * Render free tier é pequeno e um vazamento de temp derruba a instância.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FfmpegFacelessVideoRenderer implements FacelessVideoRenderer {

    private final FacelessProperties props;
    private final StorageClient storageClient;
    private final AssSubtitleBuilder subtitleBuilder;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Override
    public String render(FacelessRenderInput input) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("faceless-" + input.jobId());

            // 1) Áudio em disco
            Path audio = workDir.resolve("audio.mp3");
            Files.write(audio, input.audioMp3());

            double duration = input.durationSeconds() > 0
                ? input.durationSeconds()
                : probeDurationSeconds(audio);

            // 2) Legendas ASS
            Path ass = workDir.resolve("legendas.ass");
            String assContent = subtitleBuilder.build(
                input.narrationText(), input.wordTimings(), duration, props);
            Files.write(ass, assContent.getBytes(StandardCharsets.UTF_8));

            // 3) Fundo: imagem de produto (Ken Burns) ou gradiente de marca
            Path image = maybeDownloadImage(input, workDir);

            // 4) ffmpeg
            Path output = workDir.resolve("out.mp4");
            List<String> command = (image != null)
                ? buildImageCommand(audio, image, ass, output, duration)
                : buildGradientCommand(audio, ass, output);
            runFfmpeg(command, workDir);

            // 5) Upload no R2 (mesmo StorageClient do restante do pipeline)
            byte[] mp4 = Files.readAllBytes(output);
            String videoUrl = storageClient.uploadVideo(mp4, input.jobId());
            log.info("[Job {}] Vídeo FACELESS renderizado: {} ({} bytes)",
                input.jobId(), videoUrl, mp4.length);
            return videoUrl;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("[Job {}] Falha ao renderizar vídeo FACELESS: {}", input.jobId(), e.getMessage());
            throw new RuntimeException("Falha ao renderizar vídeo. Tente novamente.", e);
        } finally {
            cleanup(workDir);
        }
    }

    // ── Comandos ffmpeg ───────────────────────────────────────────────────

    /** Fundo = imagem do produto com Ken Burns + backdrop borrado (cobre qualquer aspect ratio). */
    private List<String> buildImageCommand(Path audio, Path image, Path ass, Path output, double duration) {
        int w = props.getWidth(), h = props.getHeight(), fps = props.getFps();
        long frames = Math.max(1, Math.round(duration * fps));
        String filter =
            "[0:v]scale=" + w + ":" + h + ":force_original_aspect_ratio=increase," +
            "crop=" + w + ":" + h + ",setsar=1,boxblur=40:1[bg];" +
            "[0:v]scale=" + w + ":" + h + ":force_original_aspect_ratio=decrease,setsar=1[fg];" +
            "[bg][fg]overlay=(W-w)/2:(H-h)/2[comp];" +
            "[comp]zoompan=z='min(zoom+0.0006,1.5)':d=" + frames +
            ":x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s=" + w + "x" + h + ":fps=" + fps + "[bz];" +
            "[bz]ass=" + assPath(ass) + "[v]";

        List<String> cmd = new ArrayList<>(List.of(
            props.getFfmpegPath(), "-y",
            "-loop", "1", "-framerate", String.valueOf(fps), "-i", image.toString(),
            "-i", audio.toString(),
            "-filter_complex", filter,
            "-map", "[v]", "-map", "1:a"
        ));
        cmd.addAll(encodeArgs());
        cmd.add(output.toString());
        return cmd;
    }

    /** Fundo = gradiente de marca (sem imagem de produto). */
    private List<String> buildGradientCommand(Path audio, Path ass, Path output) {
        int w = props.getWidth(), h = props.getHeight(), fps = props.getFps();
        String src = "gradients=s=" + w + "x" + h +
            ":c0=0x2B1C66:c1=0x0B0A18:x0=0:y0=0:x1=0:y1=" + h + ":speed=0.01:r=" + fps;

        List<String> cmd = new ArrayList<>(List.of(
            props.getFfmpegPath(), "-y",
            "-f", "lavfi", "-i", src,
            "-i", audio.toString(),
            "-vf", "ass=" + assPath(ass),
            "-map", "0:v", "-map", "1:a"
        ));
        cmd.addAll(encodeArgs());
        cmd.add(output.toString());
        return cmd;
    }

    private List<String> encodeArgs() {
        return List.of(
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-r", String.valueOf(props.getFps()),
            "-c:a", "aac", "-b:a", "128k",
            "-shortest", "-movflags", "+faststart"
        );
    }

    // ── ffmpeg / ffprobe ──────────────────────────────────────────────────

    private void runFfmpeg(List<String> command, Path workDir) throws IOException, InterruptedException {
        log.debug("Executando ffmpeg: {}", String.join(" ", command));
        Process process = new ProcessBuilder(command)
            .directory(workDir.toFile())
            .redirectErrorStream(true)
            .start();

        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("ffmpeg excedeu o tempo limite de renderização.");
        }
        int exit = process.exitValue();
        if (exit != 0) {
            log.warn("ffmpeg saiu com código {} — stderr:\n{}", exit, tail(out));
            throw new IOException("ffmpeg falhou (exit code " + exit + ").");
        }
    }

    private double probeDurationSeconds(Path audio) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
            props.getFfprobePath(), "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            audio.toString()
        ).redirectErrorStream(true).start();

        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        try {
            return Double.parseDouble(out);
        } catch (NumberFormatException e) {
            log.warn("ffprobe não retornou duração ({}). Usando 0.", out);
            return 0;
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Baixa a imagem de produto se o modo de fundo for {@code product} e houver URL. */
    private Path maybeDownloadImage(FacelessRenderInput input, Path workDir) {
        if (!"product".equalsIgnoreCase(props.getBackground())) return null;
        String url = input.productImageUrl();
        if (url == null || url.isBlank()) return null;
        try {
            HttpResponse<byte[]> resp = httpClient.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200 || resp.body().length == 0) {
                log.warn("Imagem de produto indisponível (status {}). Usando gradiente.", resp.statusCode());
                return null;
            }
            Path image = workDir.resolve("product.img");
            Files.write(image, resp.body());
            return image;
        } catch (Exception e) {
            log.warn("Falha ao baixar imagem de produto ({}). Usando gradiente.", e.getMessage());
            return null;
        }
    }

    /** Caminho do ASS escapado para uso dentro de um filtro ffmpeg. */
    private static String assPath(Path ass) {
        // ffmpeg usa ':' e '\' como separadores dentro de filtros — escapa para o filename.
        return ass.toString().replace("\\", "\\\\").replace(":", "\\:");
    }

    private static String tail(String s) {
        if (s == null) return "";
        String[] lines = s.split("\n");
        int from = Math.max(0, lines.length - 25);
        return String.join("\n", java.util.Arrays.copyOfRange(lines, from, lines.length));
    }

    private void cleanup(Path workDir) {
        if (workDir == null) return;
        try (var paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException e) {
            log.warn("Falha ao limpar diretório temporário {}: {}", workDir, e.getMessage());
        }
    }
}
