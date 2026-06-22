package br.com.linkflow.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuração da integração com a Shopee Affiliate Open API (env vars — só
 * backend).
 *
 * <pre>
 * shopee.enabled=true
 * shopee.app-id=...
 * shopee.secret=...
 * shopee.keyword-by-category.eletronicos=eletrônicos
 * </pre>
 *
 * <p>A API de afiliado da Shopee já devolve a comissão real por produto
 * ({@code commissionRate}) e o link de afiliado pronto ({@code offerLink}),
 * então — diferente do Mercado Livre — não há tabela de comissão por categoria.</p>
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "shopee")
public class ShopeeProperties {

    /** Liga/desliga o provider real da Shopee. */
    private boolean enabled = false;

    /** Credenciais da Shopee Affiliate Open API (painel de afiliados → Open API). */
    private String appId;
    private String secret;

    /**
     * Endpoint GraphQL da Affiliate Open API. Configurável para testes
     * (MockWebServer). Em produção usa o endpoint oficial (Brasil).
     */
    private String apiBaseUrl = "https://open-api.affiliate.shopee.com.br/graphql";

    /** Itens buscados por categoria a cada ciclo de sync. */
    private int limitPerCategory = 20;

    /**
     * Ordenação da busca {@code productOfferV2}: 1=Relevância, 2=Vendas,
     * 3=Preço↓, 4=Preço↑, 5=Comissão↓. Default 2 (mais vendidos) para o radar.
     */
    private int sortType = 2;

    /** Pausa (ms) entre categorias para respeitar o rate limit da Shopee. */
    private long throttleMs = 300;

    /**
     * Termo de busca por categoria do LinkFlow. As chaves deste mapa definem
     * quais categorias o provider coleta (uma busca por keyword cada).
     */
    private Map<String, String> keywordByCategory = new LinkedHashMap<>();
}