package br.com.linkflow.service;

import br.com.linkflow.client.ClaudeClient;
import br.com.linkflow.dto.request.LinkRequest;
import br.com.linkflow.dto.request.RegisterRequest;
import br.com.linkflow.entity.AffiliateLink;
import br.com.linkflow.entity.Click;
import br.com.linkflow.entity.User;
import br.com.linkflow.repository.AffiliateLinkRepository;
import br.com.linkflow.repository.ClickRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnalyticsServiceTest {

    @Autowired AnalyticsService analyticsService;
    @Autowired AuthService authService;
    @Autowired LinkService linkService;
    @Autowired AffiliateLinkRepository linkRepository;
    @Autowired ClickRepository clickRepository;
    @MockBean  ClaudeClient claudeClient;

    private User usuario;

    @BeforeEach
    void setup() {
        when(claudeClient.completar(anyString()))
            .thenReturn("Continue publicando conteúdo regularmente!");

        var auth = authService.register(
            new RegisterRequest("João", "joao@analytics.com", "Senha@123")
        );
        usuario = new User();
        usuario.setId(auth.user().id());
        usuario.setEmail(auth.user().email());
        usuario.setName("João");
        usuario.setPlan(User.Plan.FREE);
    }

    @Test
    @DisplayName("Dashboard deve retornar totais zerados para usuário novo")
    void dashboardUsuarioNovo() {
        var dashboard = analyticsService.dashboard(usuario);

        assertThat(dashboard.totais().totalLinks()).isZero();
        assertThat(dashboard.totais().totalCliques()).isZero();
        assertThat(dashboard.topLinks()).isEmpty();
        assertThat(dashboard.insightSemanal()).isNotBlank();
    }

    @Test
    @DisplayName("Dashboard deve contabilizar links criados")
    void dashboardComLinks() {
        linkService.criar(new LinkRequest(
            "https://mercadolivre.com.br/airfryer",
            "Airfryer Philips", null, null, null, null
        ), usuario);

        linkService.criar(new LinkRequest(
            "https://shopee.com.br/smartwatch",
            "Smartwatch", null, null, null, null
        ), usuario);

        var dashboard = analyticsService.dashboard(usuario);
        assertThat(dashboard.totais().totalLinks()).isEqualTo(2);
    }

    @Test
    @DisplayName("Dashboard deve mostrar top links por cliques")
    void dashboardTopLinks() {
        var link = linkService.criar(new LinkRequest(
            "https://mercadolivre.com.br/airfryer",
            "Airfryer Philips", null, null, null, null
        ), usuario);

        // Simula 5 cliques no link
        AffiliateLink affiliateLink = linkRepository.findById(link.id()).orElseThrow();
        for (int i = 0; i < 5; i++) {
            clickRepository.save(Click.builder()
                .link(affiliateLink)
                .device(Click.Device.MOBILE)
                .ipHash("192.168.1.xxx")
                .build());
        }
        affiliateLink.setClicks(5L);
        linkRepository.save(affiliateLink);

        var dashboard = analyticsService.dashboard(usuario);
        assertThat(dashboard.topLinks()).isNotEmpty();
        assertThat(dashboard.topLinks().get(0).title()).isEqualTo("Airfryer Philips");
        assertThat(dashboard.topLinks().get(0).clicks()).isEqualTo(5);
    }

    @Test
    @DisplayName("Dashboard deve retornar uso do plano FREE corretamente")
    void dashboardUsoPlano() {
        var dashboard = analyticsService.dashboard(usuario);
        var uso = dashboard.usoPlano();

        assertThat(uso.plano()).isEqualTo("FREE");
        assertThat(uso.limiteRoteiros()).isEqualTo(5);
        assertThat(uso.limiteVideos()).isEqualTo(2);
        assertThat(uso.limiteLinks()).isEqualTo(10);
    }

    @Test
    @DisplayName("Insight semanal deve retornar texto da IA ou fallback")
    void insightSemanal() {
        var dashboard = analyticsService.dashboard(usuario);
        assertThat(dashboard.insightSemanal()).isNotBlank();
        assertThat(dashboard.insightSemanal()).contains("conteúdo");
    }
}
