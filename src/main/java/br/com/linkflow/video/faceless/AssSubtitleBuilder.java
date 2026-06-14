package br.com.linkflow.video.faceless;

import br.com.linkflow.config.FacelessProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gera um arquivo de legendas no formato ASS (Advanced SubStation Alpha) a partir
 * dos tempos de palavra da narração. O ffmpeg faz burn-in via filtro {@code ass=...}.
 *
 * <p>Classe de lógica pura (sem ffmpeg) — facilmente testável. Estilo: fonte grande,
 * contorno grosso, posição embaixo-centro, blocos curtos (3–5 palavras).
 */
@Component
public class AssSubtitleBuilder {

    /** Bloco de legenda já com tempo resolvido. */
    record Block(double startSeconds, double endSeconds, String text) {}

    /**
     * Monta o conteúdo ASS completo.
     *
     * @param narrationText   texto da narração (usado no fallback uniforme)
     * @param wordTimings     tempos por palavra; nulo/vazio aciona o fallback uniforme
     * @param durationSeconds duração total do áudio (para o fallback uniforme)
     * @param props           configuração de fonte/dimensão
     */
    public String build(String narrationText,
                        List<WordTiming> wordTimings,
                        double durationSeconds,
                        FacelessProperties props) {

        List<Block> blocks = (wordTimings != null && !wordTimings.isEmpty())
            ? groupByTimings(wordTimings, props.getMaxWordsPerBlock())
            : groupUniform(narrationText, durationSeconds, props.getMaxWordsPerBlock());

        StringBuilder sb = new StringBuilder();
        sb.append(header(props));
        for (Block b : blocks) {
            sb.append("Dialogue: 0,")
              .append(formatTime(b.startSeconds())).append(',')
              .append(formatTime(b.endSeconds())).append(',')
              .append("Default,,0,0,0,,")
              .append(escape(b.text()))
              .append('\n');
        }
        return sb.toString();
    }

    // ── Agrupamento ───────────────────────────────────────────────────────

    /** Agrupa palavras com timestamps reais em blocos de até {@code maxWords}. */
    private List<Block> groupByTimings(List<WordTiming> words, int maxWords) {
        int max = Math.max(1, maxWords);
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < words.size(); i += max) {
            List<WordTiming> chunk = words.subList(i, Math.min(i + max, words.size()));
            double start = chunk.get(0).startSeconds();
            double end = chunk.get(chunk.size() - 1).endSeconds();
            String text = String.join(" ", chunk.stream().map(WordTiming::word).toList());
            blocks.add(new Block(start, Math.max(end, start + 0.2), text));
        }
        return blocks;
    }

    /**
     * Fallback uniforme: divide o texto em blocos e distribui igualmente pela duração.
     * TODO: usar timestamps reais (ElevenLabs with-timestamps) sempre que disponíveis.
     */
    private List<Block> groupUniform(String narrationText, double durationSeconds, int maxWords) {
        int max = Math.max(1, maxWords);
        List<Block> blocks = new ArrayList<>();
        if (narrationText == null || narrationText.isBlank()) {
            return blocks;
        }
        String[] words = narrationText.trim().split("\\s+");
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int count = 0;
        for (String w : words) {
            if (count == max) {
                chunks.add(cur.toString().trim());
                cur.setLength(0);
                count = 0;
            }
            cur.append(w).append(' ');
            count++;
        }
        if (cur.length() > 0) chunks.add(cur.toString().trim());

        // Sem duração confiável, assume 2s por bloco.
        double total = durationSeconds > 0 ? durationSeconds : chunks.size() * 2.0;
        double per = chunks.isEmpty() ? total : total / chunks.size();
        for (int i = 0; i < chunks.size(); i++) {
            blocks.add(new Block(i * per, (i + 1) * per, chunks.get(i)));
        }
        return blocks;
    }

    // ── Formatação ASS ──────────────────────────────────────────────────────

    private String header(FacelessProperties props) {
        // Alignment 2 = embaixo-centro; Outline grosso (6) para legibilidade.
        return """
            [Script Info]
            ScriptType: v4.00+
            PlayResX: %d
            PlayResY: %d
            WrapStyle: 0
            ScaledBorderAndShadow: yes

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
            Style: Default,%s,84,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,-1,0,0,0,100,100,0,0,1,6,2,2,80,80,260,1

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            """.formatted(props.getWidth(), props.getHeight(), props.getFontName());
    }

    /** Segundos → {@code H:MM:SS.cs} (centésimos), formato exigido pelo ASS. */
    static String formatTime(double seconds) {
        if (seconds < 0) seconds = 0;
        int totalCs = (int) Math.round(seconds * 100);
        int cs = totalCs % 100;
        int totalSec = totalCs / 100;
        int s = totalSec % 60;
        int m = (totalSec / 60) % 60;
        int h = totalSec / 3600;
        return String.format(Locale.ROOT, "%d:%02d:%02d.%02d", h, m, s, cs);
    }

    /** Escapa o texto para o campo Text do ASS (chaves = override codes; \\N = quebra). */
    static String escape(String text) {
        if (text == null) return "";
        return text.replace("{", "(")
                   .replace("}", ")")
                   .replace("\r", "")
                   .replace("\n", "\\N")
                   .trim();
    }
}
