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
public class ClaudeClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    @Value("${linkflow.claude.api-key}")
    private String apiKey;

    @Value("${linkflow.claude.model}")
    private String model;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final ObjectMapper objectMapper;

    public String completar(String prompt) {
        try {
            String body = objectMapper.writeValueAsString(new ClaudeRequest(
                model,
                1500,
                new ClaudeRequest.Message[]{ new ClaudeRequest.Message("user", prompt) }
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Erro na API Claude: status={} body={}", response.statusCode(), response.body());
                throw new RuntimeException("Erro na API Claude: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("content").get(0).path("text").asText();

        } catch (Exception e) {
            log.error("Falha ao chamar Claude API: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao gerar conteúdo com IA. Tente novamente.");
        }
    }

    // Records internos para serialização
    record ClaudeRequest(String model, int max_tokens, Message[] messages) {
        record Message(String role, String content) {}
    }
}
