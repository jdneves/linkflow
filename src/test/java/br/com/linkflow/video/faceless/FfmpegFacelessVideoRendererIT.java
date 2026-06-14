package br.com.linkflow.video.faceless;

import br.com.linkflow.client.StorageClient;
import br.com.linkflow.config.FacelessProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integração real com ffmpeg — só roda onde ffmpeg/ffprobe estão disponíveis
 * (via assumeTrue). Em CI sem ffmpeg, os testes são pulados.
 * Tag "ffmpeg" permite filtrar explicitamente.
 */
@Tag("ffmpeg")
class FfmpegFacelessVideoRendererIT {

    private static boolean ffmpegAvailable;

    @BeforeAll
    static void checkFfmpeg() {
        ffmpegAvailable = commandExists("ffmpeg") && commandExists("ffprobe");
    }

    @Test
    @DisplayName("Deve renderizar MP4 com duração ≈ duração do áudio")
    void deveRenderizarMp4(@TempDir Path tmp) throws Exception {
        assumeTrue(ffmpegAvailable, "ffmpeg/ffprobe ausentes — pulando teste de integração");

        // Áudio fake de 3s (silêncio) gerado pelo próprio ffmpeg
        Path fakeAudio = tmp.resolve("fake.mp3");
        runOk("ffmpeg", "-y", "-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono",
            "-t", "3", "-q:a", "9", fakeAudio.toString());
        byte[] audioBytes = Files.readAllBytes(fakeAudio);

        // Captura o MP4 que seria enviado ao R2
        StorageClient storage = mock(StorageClient.class);
        ArgumentCaptor<byte[]> mp4Captor = ArgumentCaptor.forClass(byte[].class);
        when(storage.uploadVideo(any(), any())).thenReturn("https://r2.example.com/videos/x.mp4");

        FacelessProperties props = new FacelessProperties();
        FfmpegFacelessVideoRenderer renderer =
            new FfmpegFacelessVideoRenderer(props, storage, new AssSubtitleBuilder());

        FacelessRenderInput input = new FacelessRenderInput(
            UUID.randomUUID(),
            audioBytes,
            0, // força ffprobe
            "produto incrível em promoção hoje",
            List.of(
                new WordTiming("produto", 0.0, 0.6),
                new WordTiming("incrível", 0.6, 1.4),
                new WordTiming("em", 1.4, 1.6),
                new WordTiming("promoção", 1.6, 2.4),
                new WordTiming("hoje", 2.4, 3.0)
            ),
            null, // sem imagem → caminho gradiente
            "Produto Teste"
        );

        String url = renderer.render(input);
        assertThat(url).isEqualTo("https://r2.example.com/videos/x.mp4");

        verify(storage).uploadVideo(mp4Captor.capture(), any());
        byte[] mp4 = mp4Captor.getValue();
        assertThat(mp4).isNotEmpty();

        Path out = tmp.resolve("out.mp4");
        Files.write(out, mp4);
        double duration = probeDuration(out);
        assertThat(duration).isBetween(2.5, 3.6);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static boolean commandExists(String cmd) {
        try {
            return new ProcessBuilder(cmd, "-version")
                .redirectErrorStream(true).start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void runOk(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        assertThat(p.waitFor(2, TimeUnit.MINUTES)).isTrue();
        assertThat(p.exitValue()).isZero();
    }

    private static double probeDuration(Path file) throws Exception {
        Process p = new ProcessBuilder("ffprobe", "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1", file.toString())
            .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor(30, TimeUnit.SECONDS);
        return Double.parseDouble(out);
    }
}