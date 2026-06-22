package br.com.linkflow.provider;

import br.com.linkflow.client.ShopeeClient;
import br.com.linkflow.config.ShopeeProperties;
import br.com.linkflow.entity.Product.Platform;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShopeeProductProviderTest {

    private MockWebServer server;
    private ShopeeProductProvider provider;
    private ShopeeProperties props;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        props = new ShopeeProperties();
        props.setEnabled(true);
        props.setAppId("APP123");
        props.setSecret("SECRET456");
        props.setApiBaseUrl(server.url("/graphql").toString());
        props.setLimitPerCategory(10);
        props.setThrottleMs(0); // sem pausa nos testes
        props.getKeywordByCategory().put("eletronicos", "fone");

        ShopeeClient client = new ShopeeClient(props, new ObjectMapper());
        provider = new ShopeeProductProvider(client, props);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    @DisplayName("isEnabled reflete o flag de config e a plataforma é SHOPEE")
    void deveReportarEstado() {
        assertThat(provider.platform()).isEqualTo(Platform.SHOPEE);
        assertThat(provider.isEnabled()).isTrue();
        props.setEnabled(false);
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Deve mapear node da Shopee para RawProduct com comissão, link de afiliado e preço original")
    void deveMapearNodeParaRawProduct() {
        server.enqueue(umProduto());

        List<RawProduct> catalog = provider.fetchCatalog();

        assertThat(catalog).hasSize(1);
        RawProduct p = catalog.get(0);
        assertThat(p.platform()).isEqualTo(Platform.SHOPEE);
        assertThat(p.externalId()).isEqualTo("123");
        assertThat(p.name()).isEqualTo("Fone X");
        assertThat(p.category()).isEqualTo("eletronicos");
        assertThat(p.price()).isEqualByComparingTo("99.90");
        // commissionRate 0.15 → 15%.
        assertThat(p.commissionPct()).isEqualByComparingTo("15.00");
        // priceDiscountRate 20% → original = 99.90 * 100 / 80 = 124.88.
        assertThat(p.originalPrice()).isEqualByComparingTo("124.88");
        // offerLink já é o link de afiliado pronto.
        assertThat(p.productUrl()).isEqualTo("https://s.shopee.com.br/abc");
    }

    @Test
    @DisplayName("Deve autenticar com assinatura SHA256(appId+timestamp+payload+secret)")
    void deveAssinarRequisicao() throws Exception {
        server.enqueue(umProduto());

        provider.fetchCatalog();

        RecordedRequest request = server.takeRequest();
        String payload = request.getBody().readUtf8();
        String auth = request.getHeader("Authorization");
        assertThat(auth).startsWith("SHA256 Credential=APP123,");

        String timestamp = extrair(auth, "Timestamp=");
        String signature = extrair(auth, "Signature=");
        String esperada = sha256Hex("APP123" + timestamp + payload + "SECRET456");
        assertThat(signature).isEqualTo(esperada);
    }

    @Test
    @DisplayName("Instrumenta a coleta: loga contagem por categoria e avisa categoria vazia")
    void deveInstrumentarContagemPorCategoria() {
        props.getKeywordByCategory().put("beleza", "vazio"); // segunda categoria, sem resultado
        server.enqueue(umProduto());           // eletronicos
        server.enqueue(nenhumProduto());       // beleza

        Logger logger = (Logger) LoggerFactory.getLogger(ShopeeProductProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        provider.fetchCatalog();

        logger.detachAppender(appender);
        List<ILoggingEvent> logs = appender.list;

        assertThat(logs).anyMatch(e -> e.getFormattedMessage().contains("Itens por categoria")
            && e.getFormattedMessage().contains("eletronicos=1"));
        assertThat(logs).anyMatch(e -> e.getLevel() == Level.WARN
            && e.getFormattedMessage().contains("beleza")
            && e.getFormattedMessage().contains("não retornou produtos"));
    }

    private static MockResponse umProduto() {
        return json("""
            {"data":{"productOfferV2":{"nodes":[
              {"itemId":"123","productName":"Fone X","priceMin":"99.90","priceMax":"99.90",
               "imageUrl":"https://img/x.jpg","offerLink":"https://s.shopee.com.br/abc",
               "commissionRate":"0.15","sales":120,"priceDiscountRate":20}
            ],"pageInfo":{"page":1,"hasNextPage":false}}}}
            """);
    }

    private static MockResponse nenhumProduto() {
        return json("""
            {"data":{"productOfferV2":{"nodes":[],"pageInfo":{"page":1,"hasNextPage":false}}}}
            """);
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private static String extrair(String auth, String chave) {
        int i = auth.indexOf(chave) + chave.length();
        int fim = auth.indexOf(',', i);
        return (fim < 0 ? auth.substring(i) : auth.substring(i, fim)).trim();
    }

    private static String sha256Hex(String data) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
            .digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
