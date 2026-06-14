package br.com.linkflow.client;

import br.com.linkflow.video.faceless.WordTiming;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
public class ElevenLabsClient {

    private static final String BASE_URL = "https://api.elevenlabs.io/v1";

    @Value("${linkflow.elevenlabs.api-key}")
    private String apiKey;

    @Value("${linkflow.elevenlabs.default-voice-id}")
    private String defaultVoiceId;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /**
     * Converte texto em áudio MP3.
     * Retorna os bytes do arquivo MP3 gerado.
     */
    public byte[] textToSpeech(String texto, String voiceId) {
        String voz = voiceId != null ? voiceId : defaultVoiceId;
        String url = BASE_URL + "/text-to-speech/" + voz;

        String body = """
            {
              "text": %s,
              "model_id": "eleven_multilingual_v2",
              "voice_settings": {
                "stability": 0.5,
                "similarity_boost": 0.75,
                "style": 0.3,
                "use_speaker_boost": true
              }
            }
            """.formatted(toJsonString(texto));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(60))
                .build();

            HttpResponse<byte[]> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.error("ElevenLabs erro: status={}", response.statusCode());
                throw new RuntimeException("ElevenLabs retornou status " + response.statusCode());
            }

            log.info("Áudio gerado com sucesso: {} bytes", response.body().length);
            return response.body();

        } catch (Exception e) {
            log.error("Falha ao gerar áudio: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao gerar narração. Tente novamente.");
        }
    }

    /**
     * Converte texto em áudio MP3 com alinhamento temporal por caractere
     * (endpoint {@code /with-timestamps}). Usado pelo pipeline FACELESS para
     * gerar legendas sincronizadas (burn-in). Mesmo texto/voz do TTS comum.
     */
    public TimedSpeech textToSpeechWithTimestamps(String texto, String voiceId) {
        String voz = voiceId != null ? voiceId : defaultVoiceId;
        String url = BASE_URL + "/text-to-speech/" + voz + "/with-timestamps";

        String body = """
            {
              "text": %s,
              "model_id": "eleven_multilingual_v2",
              "voice_settings": {
                "stability": 0.5,
                "similarity_boost": 0.75,
                "style": 0.3,
                "use_speaker_boost": true
              }
            }
            """.formatted(toJsonString(texto));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(90))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("ElevenLabs (timestamps) erro: status={}", response.statusCode());
                throw new RuntimeException("ElevenLabs retornou status " + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());
            byte[] audio = Base64.getDecoder().decode(json.path("audio_base64").asText());

            JsonNode alignment = json.path("alignment");
            List<String> chars = new ArrayList<>();
            List<Double> starts = new ArrayList<>();
            List<Double> ends = new ArrayList<>();
            alignment.path("characters").forEach(n -> chars.add(n.asText()));
            alignment.path("character_start_times_seconds").forEach(n -> starts.add(n.asDouble()));
            alignment.path("character_end_times_seconds").forEach(n -> ends.add(n.asDouble()));

            List<WordTiming> words = charsToWords(chars, starts, ends);
            double duration = ends.isEmpty() ? 0 : ends.get(ends.size() - 1);

            log.info("Áudio (com timestamps) gerado: {} bytes, {} palavras, {}s",
                audio.length, words.size(), duration);
            return new TimedSpeech(audio, duration, words);

        } catch (Exception e) {
            log.error("Falha ao gerar áudio com timestamps: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao gerar narração. Tente novamente.");
        }
    }

    /**
     * Converte o alinhamento por caractere do ElevenLabs em tempos por palavra.
     * Quebra em espaços; o início da palavra é o início do 1º caractere e o fim
     * é o fim do último caractere antes do próximo espaço.
     */
    static List<WordTiming> charsToWords(List<String> chars, List<Double> starts, List<Double> ends) {
        List<WordTiming> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        double wordStart = 0;
        double wordEnd = 0;
        boolean inWord = false;

        int n = Math.min(chars.size(), Math.min(starts.size(), ends.size()));
        for (int i = 0; i < n; i++) {
            String c = chars.get(i);
            boolean isSpace = c.isBlank();
            if (isSpace) {
                if (inWord) {
                    words.add(new WordTiming(word.toString(), wordStart, wordEnd));
                    word.setLength(0);
                    inWord = false;
                }
            } else {
                if (!inWord) {
                    wordStart = starts.get(i);
                    inWord = true;
                }
                word.append(c);
                wordEnd = ends.get(i);
            }
        }
        if (inWord) {
            words.add(new WordTiming(word.toString(), wordStart, wordEnd));
        }
        return words;
    }

    /** Áudio MP3 + duração (s) + tempos por palavra. */
    public record TimedSpeech(byte[] audio, double durationSeconds, List<WordTiming> wordTimings) {}

    /**
     * Lista vozes disponíveis na conta.
     */
    public String listarVozes() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/voices"))
                .header("xi-api-key", apiKey)
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            return response.body();
        } catch (Exception e) {
            log.error("Falha ao listar vozes: {}", e.getMessage());
            throw new RuntimeException("Falha ao listar vozes.");
        }
    }

    // Escapa o texto para JSON com segurança
    private String toJsonString(String texto) {
        return "\"" + texto
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            + "\"";
    }
}
