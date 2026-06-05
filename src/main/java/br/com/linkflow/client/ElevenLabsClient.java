package br.com.linkflow.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Component
public class ElevenLabsClient {

    private static final String BASE_URL = "https://api.elevenlabs.io/v1";

    @Value("${linkflow.elevenlabs.api-key}")
    private String apiKey;

    @Value("${linkflow.elevenlabs.default-voice-id}")
    private String defaultVoiceId;

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
