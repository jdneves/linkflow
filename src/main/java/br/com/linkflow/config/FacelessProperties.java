package br.com.linkflow.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuração da renderização FACELESS (ffmpeg). Defaults seguros para
 * produção em container Debian com ffmpeg + fonts-dejavu instalados.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "faceless")
public class FacelessProperties {

    /** Largura do vídeo (9:16 vertical). */
    private int width = 1080;

    /** Altura do vídeo (9:16 vertical). */
    private int height = 1920;

    /** Frames por segundo. */
    private int fps = 30;

    /** Caminho do TTF usado para as legendas (Debian + fonts-dejavu-core). */
    private String fontPath = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf";

    /** Nome da fonte como resolvido pelo fontconfig/libass. */
    private String fontName = "DejaVu Sans";

    /** Estratégia de fundo: {@code gradient} (sólido/gradiente de marca) ou {@code product} (imagem). */
    private String background = "gradient";

    /** Executável do ffmpeg (no PATH do container). */
    private String ffmpegPath = "ffmpeg";

    /** Executável do ffprobe (no PATH do container). */
    private String ffprobePath = "ffprobe";

    /** Máximo de palavras por bloco de legenda (curto e legível). */
    private int maxWordsPerBlock = 4;
}
