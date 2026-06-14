package br.com.linkflow.video.faceless;

import java.util.List;
import java.util.UUID;

/**
 * Entrada para renderização de um vídeo FACELESS (sem avatar).
 *
 * @param jobId           id do {@code VideoJob} — usado para nomear o arquivo no R2
 * @param audioMp3        bytes da locução já gerada (ElevenLabs)
 * @param durationSeconds duração do áudio em segundos; {@code <= 0} faz o renderer
 *                        descobrir via ffprobe
 * @param narrationText   texto completo da narração (fallback de legenda sem timings)
 * @param wordTimings     tempos por palavra; nulo/vazio aciona distribuição uniforme
 * @param productImageUrl imagem de produto opcional para o fundo (pode ser nula)
 * @param productName     nome do produto (metadados / futuro overlay de título)
 */
public record FacelessRenderInput(
    UUID jobId,
    byte[] audioMp3,
    double durationSeconds,
    String narrationText,
    List<WordTiming> wordTimings,
    String productImageUrl,
    String productName
) {}