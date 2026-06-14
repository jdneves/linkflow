package br.com.linkflow.video.faceless;

import br.com.linkflow.config.FacelessProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssSubtitleBuilderTest {

    private final AssSubtitleBuilder builder = new AssSubtitleBuilder();
    private final FacelessProperties props = new FacelessProperties();

    @Test
    @DisplayName("Deve gerar ASS com cabeçalho e estilo Default")
    void deveGerarCabecalho() {
        String ass = builder.build("Olá mundo", List.of(), 2.0, props);

        assertThat(ass)
            .contains("[Script Info]")
            .contains("PlayResX: 1080")
            .contains("PlayResY: 1920")
            .contains("[V4+ Styles]")
            .contains("Style: Default,DejaVu Sans")
            .contains("[Events]");
    }

    @Test
    @DisplayName("Deve usar timestamps reais quando disponíveis")
    void deveUsarTimestampsReais() {
        List<WordTiming> timings = List.of(
            new WordTiming("Esse", 0.0, 0.4),
            new WordTiming("produto", 0.4, 1.0),
            new WordTiming("é", 1.0, 1.2),
            new WordTiming("incrível", 1.2, 2.0),
            new WordTiming("mesmo", 2.0, 2.6)
        );

        String ass = builder.build("Esse produto é incrível mesmo", timings, 2.6, props);

        // maxWordsPerBlock = 4 → bloco 1 = palavras 0–3 (0.0–2.0), bloco 2 = "mesmo" (2.0–2.6)
        assertThat(ass).contains("Dialogue: 0,0:00:00.00,0:00:02.00,Default,,0,0,0,,Esse produto é incrível");
        assertThat(ass).contains("Dialogue: 0,0:00:02.00,0:00:02.60,Default,,0,0,0,,mesmo");
    }

    @Test
    @DisplayName("Fallback uniforme deve distribuir blocos igualmente pela duração")
    void deveDistribuirUniformemente() {
        // 8 palavras, 4 por bloco → 2 blocos; duração 10s → 5s cada
        String texto = "um dois três quatro cinco seis sete oito";
        String ass = builder.build(texto, null, 10.0, props);

        assertThat(ass).contains("Dialogue: 0,0:00:00.00,0:00:05.00,Default,,0,0,0,,um dois três quatro");
        assertThat(ass).contains("Dialogue: 0,0:00:05.00,0:00:10.00,Default,,0,0,0,,cinco seis sete oito");
    }

    @Test
    @DisplayName("Deve escapar chaves e quebras de linha no texto")
    void deveEscaparTexto() {
        List<WordTiming> timings = List.of(new WordTiming("a{b}c", 0.0, 1.0));
        String ass = builder.build("x", timings, 1.0, props);
        assertThat(ass).contains("a(b)c");
        assertThat(ass).doesNotContain("a{b}c");
    }

    @Test
    @DisplayName("Texto vazio não deve gerar linhas de diálogo")
    void textoVazioSemDialogo() {
        String ass = builder.build("   ", null, 5.0, props);
        assertThat(ass).doesNotContain("Dialogue:");
    }

    @Test
    @DisplayName("Deve formatar tempo no padrão H:MM:SS.cs")
    void deveFormatarTempo() {
        assertThat(AssSubtitleBuilder.formatTime(0)).isEqualTo("0:00:00.00");
        assertThat(AssSubtitleBuilder.formatTime(3.45)).isEqualTo("0:00:03.45");
        assertThat(AssSubtitleBuilder.formatTime(65.0)).isEqualTo("0:01:05.00");
        assertThat(AssSubtitleBuilder.formatTime(3661.5)).isEqualTo("1:01:01.50");
    }
}