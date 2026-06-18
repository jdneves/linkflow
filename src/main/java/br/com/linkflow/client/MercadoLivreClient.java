package br.com.linkflow.client;

import br.com.linkflow.config.MercadoLivreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente HTTP para a API do Mercado Livre, seguindo o padrão dos demais
 * clients do projeto (ClaudeClient/HeyGenClient).
 *
 * <p>Endpoints conforme a documentação oficial (developers.mercadolivre.com.br).
 * A busca pública por site ({@code /sites/{site}/search}) e o detalhe de item
 * ({@code /items/{id}}) hoje retornam 403 para apps gerais, então a coleta usa
 * os destaques (mais vendidos) da categoria + detalhe do produto de catálogo:
 * <ul>
 *   <li>OAuth: {@code POST /oauth/token} (grant_type=refresh_token)</li>
 *   <li>Destaques: {@code GET /highlights/{site}/category/{id}} → IDs de produto</li>
 *   <li>Produto: {@code GET /products/{id}} → nome e imagem</li>
 *   <li>Ofertas: {@code GET /products/{id}/items} → preço e preço original</li>
 * </ul>
 *
 * <p>O access token é curto; um {@link TokenManager} interno faz cache e
 * refresh automático na expiração. Toda chamada externa é protegida com
 * {@code log.warn} (convenção do projeto) — falhas não derrubam o sync.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MercadoLivreClient {

    private final MercadoLivreProperties props;
    private final ObjectMapper objectMapper;
    private final MercadoLivreTokenStore tokenStore;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final TokenManager tokenManager = new TokenManager();

    /**
     * Coleta os produtos em destaque (mais vendidos) de uma categoria do Mercado
     * Livre, até {@code limit} itens. Para cada produto de catálogo busca nome,
     * imagem e preço. Retorna lista vazia em qualquer falha (sem derrubar o sync).
     */
    public List<MlItem> buscarPorCategoria(String mlCategoryId, int limit) {
        try {
            String token = tokenManager.getAccessToken();

            String highlightsUrl = "%s/highlights/%s/category/%s".formatted(
                props.getApiBaseUrl(),
                URLEncoder.encode(props.getSiteId(), StandardCharsets.UTF_8),
                URLEncoder.encode(mlCategoryId, StandardCharsets.UTF_8)
            );
            JsonNode highlights = getJson(highlightsUrl, token, "destaques da categoria " + mlCategoryId);
            if (highlights == null) {
                return List.of();
            }

            List<MlItem> items = new ArrayList<>();
            for (JsonNode entry : highlights.path("content")) {
                if (items.size() >= limit) break;
                // Apenas produtos de catálogo; USER_PRODUCT exige outro endpoint (403).
                if (!"PRODUCT".equals(entry.path("type").asText())) continue;
                String productId = entry.path("id").asText(null);
                if (productId == null || productId.isBlank()) continue;

                MlItem item = fetchProduct(productId, token);
                if (item != null) items.add(item);
            }
            return items;

        } catch (Exception e) {
            log.warn("Falha ao buscar categoria {} no Mercado Livre: {}", mlCategoryId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Monta um {@link MlItem} a partir do produto de catálogo: {@code /products/{id}}
     * traz nome e imagem; {@code /products/{id}/items} traz o preço (o catálogo não
     * tem preço único). Sem nome ou sem preço o produto é descartado.
     */
    private MlItem fetchProduct(String productId, String token) {
        JsonNode product = getJson(props.getApiBaseUrl() + "/products/" + productId,
            token, "detalhe do produto " + productId);
        if (product == null) return null;

        String title = product.path("name").asText(null);
        String thumbnail = product.path("pictures").path(0).path("url").asText(null);
        String permalink = product.path("permalink").asText(null);
        // Produto de catálogo traz a descrição em short_description.content (texto puro).
        String description = product.path("short_description").path("content").asText(null);

        JsonNode offers = getJson(props.getApiBaseUrl() + "/products/" + productId + "/items?limit=1",
            token, "ofertas do produto " + productId);
        JsonNode offer = offers != null ? offers.path("results").path(0) : null;

        BigDecimal price = offer != null ? bigDecimalOrNull(offer, "price") : null;
        BigDecimal originalPrice = offer != null ? bigDecimalOrNull(offer, "original_price") : null;
        String categoryId = offer != null ? offer.path("category_id").asText(null) : null;

        if (title == null || title.isBlank() || price == null) return null;

        // O produto de catálogo costuma vir sem permalink; montamos a URL do PDP.
        if (permalink == null || permalink.isBlank()) {
            permalink = "https://www.mercadolivre.com.br/p/" + productId;
        }
        return new MlItem(productId, title, description, price, originalPrice, thumbnail, permalink, categoryId);
    }

    /**
     * GET autenticado que devolve o JSON do corpo, ou {@code null} em qualquer
     * status diferente de 200 (logado com {@code log.warn}, sem derrubar o sync).
     */
    private JsonNode getJson(String url, String token, String contexto) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(20));
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.warn("Mercado Livre negou acesso a {} (status={}). "
                    + "Verifique o token/permissões do afiliado.", contexto, response.statusCode());
                return null;
            }
            if (response.statusCode() != 200) {
                log.warn("Mercado Livre retornou status {} para {}.", response.statusCode(), contexto);
                return null;
            }
            return objectMapper.readTree(response.body());

        } catch (Exception e) {
            log.warn("Falha ao consultar {} no Mercado Livre: {}", contexto, e.getMessage());
            return null;
        }
    }

    private BigDecimal bigDecimalOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || !value.isNumber()) return null;
        return value.decimalValue();
    }

    /**
     * Gerencia o access token OAuth com cache e refresh automático.
     * O Mercado Livre faz rotação do refresh token a cada renovação.
     */
    private final class TokenManager {

        private volatile String accessToken;
        private volatile Instant expiresAt = Instant.EPOCH;
        // Refresh token rotaciona a cada renovação; partimos do configurado.
        private volatile String currentRefreshToken;

        synchronized String getAccessToken() {
            // 60s de folga para evitar usar um token prestes a expirar.
            if (accessToken != null && Instant.now().isBefore(expiresAt.minusSeconds(60))) {
                return accessToken;
            }
            refresh();
            return accessToken;
        }

        private void refresh() {
            // Prioridade: token rotacionado em memória > token persistido (banco) > config (env).
            // O persistido reflete a última rotação e sobrevive a restart; a env é só o seed inicial.
            String refreshToken = currentRefreshToken;
            if (refreshToken == null) {
                refreshToken = tokenStore.read().orElse(props.getRefreshToken());
            }
            if (refreshToken == null || refreshToken.isBlank()) {
                log.warn("Mercado Livre sem refresh-token configurado; chamadas seguirão sem Authorization.");
                return;
            }
            try {
                String form = "grant_type=refresh_token"
                    + "&client_id=" + enc(props.getClientId())
                    + "&client_secret=" + enc(props.getClientSecret())
                    + "&refresh_token=" + enc(refreshToken);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getApiBaseUrl() + "/oauth/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .timeout(Duration.ofSeconds(15))
                    .build();

                HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    log.warn("Mercado Livre OAuth refresh falhou (status={}): {}",
                        response.statusCode(), response.body());
                    return;
                }

                JsonNode json = objectMapper.readTree(response.body());
                accessToken = json.path("access_token").asText(null);
                long expiresIn = json.path("expires_in").asLong(0);
                expiresAt = Instant.now().plusSeconds(expiresIn > 0 ? expiresIn : 0);

                String rotated = json.path("refresh_token").asText(null);
                if (rotated != null && !rotated.isBlank() && !rotated.equals(currentRefreshToken)) {
                    currentRefreshToken = rotated;
                    tokenStore.save(rotated); // persiste para sobreviver a restart
                }
                log.debug("Mercado Livre access token renovado (expira em {}s).", expiresIn);

            } catch (Exception e) {
                log.warn("Falha ao renovar access token do Mercado Livre: {}", e.getMessage());
            }
        }

        private String enc(String value) {
            return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
        }
    }

    /** Item bruto retornado pela busca do Mercado Livre. */
    public record MlItem(
        String id,
        String title,
        String description,
        BigDecimal price,
        BigDecimal originalPrice,
        String thumbnail,
        String permalink,
        String categoryId
    ) {}
}
