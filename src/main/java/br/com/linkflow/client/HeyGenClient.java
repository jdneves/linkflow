package br.com.linkflow.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class HeyGenClient {

    private static final String BASE_URL = "https://api.heygen.com";

    @Value("${linkflow.heygen.api-key}")
    private String apiKey;

    @Value("${linkflow.heygen.default-avatar-id}")
    private String defaultAvatarId;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /**
     * Submete um job de geração de vídeo.
     * O HeyGen processa de forma assíncrona — retorna apenas o video_id.
     * Use checkStatus() para verificar quando estiver pronto.
     */
    public String submeterVideo(String audioUrl, String avatarId) {
        String avatar = avatarId != null ? avatarId : defaultAvatarId;

        String body = """
            {
              "video_inputs": [{
                "character": {
                  "type": "avatar",
                  "avatar_id": "%s",
                  "avatar_style": "normal"
                },
                "voice": {
                  "type": "audio",
                  "audio_url": "%s"
                },
                "background": {
                  "type": "color",
                  "value": "#FFFFFF"
                }
              }],
              "caption": true,
              "dimension": {
                "width": 1280,
                "height": 720
              }
            }
            """.formatted(avatar, audioUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/v2/video/generate"))
                .header("X-Api-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("HeyGen erro ao submeter: status={} body={}", response.statusCode(), response.body());
                throw new RuntimeException("HeyGen retornou status " + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String videoId = json.path("data").path("video_id").asText();
            log.info("HeyGen job submetido: video_id={}", videoId);
            return videoId;

        } catch (Exception e) {
            log.error("Falha ao submeter vídeo ao HeyGen: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao iniciar geração de vídeo.");
        }
    }

    /**
     * Verifica o status de um job no HeyGen.
     * Possíveis valores: "pending", "processing", "completed", "failed"
     */
    public VideoStatus checarStatus(String videoId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/v1/video.status.get?video_id=" + videoId))
                .header("X-Api-Key", apiKey)
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            JsonNode json   = objectMapper.readTree(response.body());
            JsonNode data   = json.path("data");
            String status   = data.path("status").asText();
            String videoUrl = data.path("video_url").asText(null);
            String error    = data.path("error").asText(null);

            log.debug("HeyGen status check: video_id={} status={}", videoId, status);
            return new VideoStatus(status, videoUrl, error);

        } catch (Exception e) {
            log.error("Falha ao checar status HeyGen: {}", e.getMessage(), e);
            return new VideoStatus("error", null, e.getMessage());
        }
    }

    /**
     * Lista avatares disponíveis na conta.
     */
    public String listarAvatares() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/v2/avatars"))
                .header("X-Api-Key", apiKey)
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            return response.body();
        } catch (Exception e) {
            log.error("Falha ao listar avatares: {}", e.getMessage());
            throw new RuntimeException("Falha ao listar avatares.");
        }
    }

    public record VideoStatus(String status, String videoUrl, String error) {
        public boolean isCompleted() { return "completed".equals(status); }
        public boolean isFailed()    { return "failed".equals(status) || "error".equals(status); }
        public boolean isProcessing(){ return "processing".equals(status) || "pending".equals(status); }
    }
}
