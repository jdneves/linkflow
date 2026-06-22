package br.com.linkflow.client;

import br.com.linkflow.config.ShopeeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente HTTP para a Shopee Affiliate Open API, seguindo o padrão dos demais
 * clients do projeto (ClaudeClient/MercadoLivreClient).
 *
 * <p>A API é um único endpoint GraphQL. Cada chamada é autenticada por uma
 * assinatura SHA256 no header {@code Authorization}:
 * <pre>
 *   Authorization: SHA256 Credential={appId}, Timestamp={ts}, Signature={sig}
 *   sig = SHA256hex(appId + ts + payload + secret)   // ts em segundos
 * </pre>
 * onde {@code payload} é exatamente o corpo JSON enviado. Usamos a query
 * {@code productOfferV2}, que já devolve comissão real, preço, imagem e o link
 * de afiliado ({@code offerLink}). Toda falha é logada com {@code log.warn} e
 * devolve lista vazia — não derruba o sync.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopeeClient {

    private final ShopeeProperties props;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /**
     * Busca ofertas de produtos para um termo de busca, até {@code limit} itens.
     * Retorna lista vazia em qualquer falha (sem derrubar o sync).
     */
    public List<ShopeeItem> buscarPorKeyword(String keyword, int limit) {
        try {
            String payload = montarPayload(keyword, limit);
            String json = chamarGraphQL(payload, "busca '" + keyword + "'");
            if (json == null) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode erros = root.path("errors");
            if (erros.isArray() && !erros.isEmpty()) {
                log.warn("Shopee retornou erro GraphQL para busca '{}': {}", keyword, erros);
                return List.of();
            }

            List<ShopeeItem> items = new ArrayList<>();
            for (JsonNode node : root.path("data").path("productOfferV2").path("nodes")) {
                ShopeeItem item = toItem(node);
                if (item != null) items.add(item);
            }
            return items;

        } catch (Exception e) {
            log.warn("Falha ao buscar '{}' na Shopee: {}", keyword, e.getMessage());
            return List.of();
        }
    }

    /** Monta o corpo JSON da query {@code productOfferV2} (inline, sem variáveis). */
    private String montarPayload(String keyword, int limit) throws Exception {
        String query = """
            query{productOfferV2(keyword:"%s",limit:%d,sortType:%d,page:1){\
            nodes{itemId productName priceMin priceMax imageUrl offerLink \
            commissionRate sales priceDiscountRate} pageInfo{page hasNextPage}}}\
            """.formatted(escaparGraphQL(keyword), limit, props.getSortType());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("query", query);
        return objectMapper.writeValueAsString(body);
    }

    /** Faz o POST GraphQL autenticado; devolve o corpo em 200, ou {@code null}. */
    private String chamarGraphQL(String payload, String contexto) {
        try {
            long timestamp = System.currentTimeMillis() / 1000L;
            String signature = assinar(props.getAppId() + timestamp + payload + props.getSecret());
            String auth = "SHA256 Credential=%s, Timestamp=%d, Signature=%s"
                .formatted(props.getAppId(), timestamp, signature);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.getApiBaseUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", auth)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(20))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.warn("Shopee negou acesso a {} (status={}). "
                    + "Verifique app-id/secret e o status no programa de afiliados.",
                    contexto, response.statusCode());
                return null;
            }
            if (response.statusCode() != 200) {
                log.warn("Shopee retornou status {} para {}.", response.statusCode(), contexto);
                return null;
            }
            return response.body();

        } catch (Exception e) {
            log.warn("Falha ao consultar {} na Shopee: {}", contexto, e.getMessage());
            return null;
        }
    }

    /** Converte um node da resposta em {@link ShopeeItem}; descarta sem nome/preço. */
    private ShopeeItem toItem(JsonNode node) {
        String itemId = node.path("itemId").asText(null);
        String name = node.path("productName").asText(null);
        BigDecimal price = bigDecimalOrNull(node.path("priceMin").asText(null));
        if (itemId == null || name == null || name.isBlank() || price == null) {
            return null;
        }

        // commissionRate vem como fração ("0.25" = 25%); convertemos para %.
        BigDecimal commissionPct = null;
        BigDecimal rate = bigDecimalOrNull(node.path("commissionRate").asText(null));
        if (rate != null) {
            commissionPct = rate.multiply(BigDecimal.valueOf(100));
        }

        // priceDiscountRate (ex.: 10 = 10%) permite reconstruir o preço original.
        BigDecimal originalPrice = null;
        int discount = node.path("priceDiscountRate").asInt(0);
        if (discount > 0 && discount < 100) {
            originalPrice = price
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(100L - discount), 2, java.math.RoundingMode.HALF_UP);
        }

        return new ShopeeItem(
            itemId,
            name,
            price,
            originalPrice,
            commissionPct,
            node.path("imageUrl").asText(null),
            node.path("offerLink").asText(null)
        );
    }

    private BigDecimal bigDecimalOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** SHA256 em hex minúsculo. */
    private String assinar(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    /** Escapa aspas/contrabarra para embutir o keyword na query GraphQL. */
    private String escaparGraphQL(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Item bruto retornado pela Affiliate Open API da Shopee. */
    public record ShopeeItem(
        String id,
        String name,
        BigDecimal price,
        BigDecimal originalPrice,
        BigDecimal commissionPct,
        String imageUrl,
        String offerLink
    ) {}
}
