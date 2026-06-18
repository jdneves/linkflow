package br.com.linkflow.client;

import br.com.linkflow.client.MercadoLivreClient.MlItem;
import br.com.linkflow.config.MercadoLivreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoLivreClientTest {

    private MockWebServer server;
    private MercadoLivreClient client;
    private InMemoryMercadoLivreTokenStore tokenStore;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        MercadoLivreProperties props = new MercadoLivreProperties();
        props.setEnabled(true);
        props.setClientId("client-123");
        props.setClientSecret("secret-456");
        props.setRefreshToken("refresh-789");
        props.setSiteId("MLB");
        props.setApiBaseUrl(server.url("/").toString().replaceAll("/$", ""));

        tokenStore = new InMemoryMercadoLivreTokenStore();
        client = new MercadoLivreClient(props, new ObjectMapper(), tokenStore);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    @DisplayName("Deve autenticar via refresh e mapear destaques da categoria (highlights → products → items)")
    void deveAutenticarEBuscar() throws Exception {
        // 1) OAuth refresh
        server.enqueue(json("""
            {"access_token":"ACCESS-TOKEN-1","token_type":"bearer",
             "expires_in":21600,"refresh_token":"refresh-rotated"}
            """));
        // 2) Destaques: 2 produtos de catálogo + 1 USER_PRODUCT (deve ser ignorado)
        server.enqueue(json("""
            {"content":[
              {"id":"MLB123","position":1,"type":"PRODUCT"},
              {"id":"MLBU000","position":2,"type":"USER_PRODUCT"},
              {"id":"MLB456","position":3,"type":"PRODUCT"}
            ]}
            """));
        // 3) Produto MLB123 (com permalink) + ofertas (com preço original)
        server.enqueue(json("""
            {"name":"Fone JBL","permalink":"https://ml/jbl",
             "short_description":{"type":"plaintext","content":"Fone bluetooth com 40h de bateria"},
             "pictures":[{"url":"https://img/jbl.jpg"}]}
            """));
        server.enqueue(json("""
            {"results":[{"price":199.90,"original_price":299.90,"category_id":"MLB1055"}]}
            """));
        // 4) Produto MLB456 (sem permalink) + ofertas (sem preço original)
        server.enqueue(json("""
            {"name":"Sem preço original","pictures":[{"url":"https://img/x.jpg"}]}
            """));
        server.enqueue(json("""
            {"results":[{"price":50.00,"category_id":"MLB1055"}]}
            """));

        List<MlItem> items = client.buscarPorCategoria("MLB1000", 20);

        // 1ª requisição: OAuth refresh
        RecordedRequest tokenReq = server.takeRequest();
        assertThat(tokenReq.getPath()).isEqualTo("/oauth/token");
        assertThat(tokenReq.getMethod()).isEqualTo("POST");
        String tokenBody = tokenReq.getBody().readUtf8();
        assertThat(tokenBody).contains("grant_type=refresh_token");
        assertThat(tokenBody).contains("client_id=client-123");
        assertThat(tokenBody).contains("refresh_token=refresh-789");

        // 2ª requisição: destaques com Bearer token
        RecordedRequest highlightsReq = server.takeRequest();
        assertThat(highlightsReq.getPath()).isEqualTo("/highlights/MLB/category/MLB1000");
        assertThat(highlightsReq.getHeader("Authorization")).isEqualTo("Bearer ACCESS-TOKEN-1");

        // 3ª requisição: detalhe do primeiro produto de catálogo
        assertThat(server.takeRequest().getPath()).isEqualTo("/products/MLB123");

        // Mapeamento — USER_PRODUCT ignorado, apenas os 2 PRODUCT
        assertThat(items).hasSize(2);
        assertThat(items.get(0).id()).isEqualTo("MLB123");
        assertThat(items.get(0).title()).isEqualTo("Fone JBL");
        assertThat(items.get(0).description()).isEqualTo("Fone bluetooth com 40h de bateria");
        assertThat(items.get(0).price()).isEqualByComparingTo("199.90");
        assertThat(items.get(0).originalPrice()).isEqualByComparingTo("299.90");
        assertThat(items.get(0).thumbnail()).isEqualTo("https://img/jbl.jpg");
        assertThat(items.get(0).permalink()).isEqualTo("https://ml/jbl");
        // Sem short_description no catálogo → descrição nula (sem quebrar o sync).
        assertThat(items.get(1).description()).isNull();
        // Sem permalink no catálogo → URL do PDP é montada a partir do id.
        assertThat(items.get(1).originalPrice()).isNull();
        assertThat(items.get(1).permalink()).isEqualTo("https://www.mercadolivre.com.br/p/MLB456");

        // O refresh token rotacionado foi persistido no store.
        assertThat(tokenStore.read()).contains("refresh-rotated");
    }

    @Test
    @DisplayName("Refresh token persistido tem prioridade sobre o configurado (env)")
    void devePriorizarTokenPersistido() throws Exception {
        // Store já tem um token rotacionado de execução anterior; deve ser usado
        // no lugar do refresh-789 vindo da config.
        tokenStore.save("refresh-do-banco");
        server.enqueue(json("{\"access_token\":\"TK\",\"expires_in\":21600}"));
        server.enqueue(json("{\"content\":[]}"));

        client.buscarPorCategoria("MLB1000", 5);

        RecordedRequest tokenReq = server.takeRequest();
        assertThat(tokenReq.getPath()).isEqualTo("/oauth/token");
        String body = tokenReq.getBody().readUtf8();
        assertThat(body).contains("refresh_token=refresh-do-banco");
        assertThat(body).doesNotContain("refresh-789");
    }

    @Test
    @DisplayName("Deve reutilizar o access token em chamadas subsequentes (sem novo refresh)")
    void deveReutilizarToken() throws Exception {
        server.enqueue(json("{\"access_token\":\"TK\",\"expires_in\":21600}"));
        server.enqueue(json("{\"content\":[]}"));
        server.enqueue(json("{\"content\":[]}"));

        client.buscarPorCategoria("MLB1000", 5);
        client.buscarPorCategoria("MLB1246", 5);

        assertThat(server.getRequestCount()).isEqualTo(3); // 1 token + 2 destaques
        assertThat(server.takeRequest().getPath()).isEqualTo("/oauth/token");
        assertThat(server.takeRequest().getPath()).startsWith("/highlights/MLB/category/");
        assertThat(server.takeRequest().getPath()).startsWith("/highlights/MLB/category/");
    }

    @Test
    @DisplayName("Erro 403 nos destaques não derruba o sync — retorna lista vazia")
    void deveTratar403() throws Exception {
        server.enqueue(json("{\"access_token\":\"TK\",\"expires_in\":21600}"));
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"message\":\"forbidden\"}"));

        List<MlItem> items = client.buscarPorCategoria("MLB1000", 5);

        assertThat(items).isEmpty();
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }
}
