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

    @Value("${linkflow.claude.mock:false}")
    private boolean mock;

    private static final String MOCK_RESPONSE = """
        {
          "titulo_sugerido": "Essa Airfryer VAI MUDAR sua cozinha! 🔥",
          "gancho_abertura": "Você ainda usa óleo pra fritar? Deixa eu te mostrar como fazer tudo isso sem uma gota de óleo — e em metade do tempo!",
          "topicos": [
            "Tecnologia Rapid Air: como circula o ar quente e elimina o óleo",
            "7 programas pré-definidos: frango, batata, peixe e mais",
            "Timer digital com desligamento automático — sem queimar nada",
            "Capacidade 4.1L: suficiente pra família inteira",
            "Economia de energia comparado ao forno convencional"
          ],
          "cta_afiliado": "Gostou? O link tá na descrição com o melhor preço que eu encontrei — aproveita porque tá em promoção!",
          "legenda_instagram": "🍟 Fritas SEM ÓLEO e com o dobro de sabor!\\n\\nFaz 3 meses que eu troquei minha fritadeira pela Airfryer Philips Walita e não volto atrás. Frango crocante, batata frita, até bolo já fiz aqui 👇\\n\\nO link com o preço especial tá na bio! ✨",
          "hashtags": ["#airfryer", "#receitassaudaveis", "#semóleo", "#philips", "#cozinhasaudavel", "#dicasdellar", "#vidadietética", "#afiliados"],
          "stories": [
            "Slide 1: 'Você sabia que dá pra fritar SEM ÓLEO? 👀'",
            "Slide 2: 'A Airfryer Philips tem 7 modos automáticos 🔥'",
            "Slide 3: 'Fiz batata frita em 15 minutos ⏱️'",
            "Slide 4: 'Capacidade pra família toda — 4.1 litros!'",
            "Slide 5: 'Link na bio com o melhor preço 👇 Corre!'"
          ]
        }
        """;


    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final ObjectMapper objectMapper;

    public String completar(String prompt) {
        if (mock) return MOCK_RESPONSE;
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
