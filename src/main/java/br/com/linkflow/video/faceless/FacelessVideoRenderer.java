package br.com.linkflow.video.faceless;

/**
 * Abstração de renderização de vídeos FACELESS (sem avatar).
 *
 * <p>A implementação atual usa ffmpeg ({@link FfmpegFacelessVideoRenderer}). A interface
 * existe para permitir trocar o motor de renderização no futuro (ex.: Remotion) sem
 * tocar no {@code VideoService}.
 */
public interface FacelessVideoRenderer {

    /**
     * Renderiza o short vertical (9:16) com fundo + locução + legendas burned-in,
     * sobe o MP4 no R2 e devolve a URL pública — mesmo contrato de saída que o
     * pipeline AVATAR usa (uma {@code videoUrl} pronta para exibir ao usuário).
     *
     * @param input dados da renderização
     * @return URL pública do MP4 no R2
     */
    String render(FacelessRenderInput input);
}