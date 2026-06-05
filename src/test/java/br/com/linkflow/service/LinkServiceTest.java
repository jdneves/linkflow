package br.com.linkflow.service;

import br.com.linkflow.dto.request.LinkRequest;
import br.com.linkflow.dto.request.RegisterRequest;
import br.com.linkflow.entity.User;
import br.com.linkflow.exception.BusinessException;
import br.com.linkflow.repository.AffiliateLinkRepository;
import br.com.linkflow.repository.ClickRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LinkServiceTest {

    @Autowired LinkService linkService;
    @Autowired AffiliateLinkRepository linkRepository;
    @Autowired ClickRepository clickRepository;
    @Autowired AuthService authService;

    private User usuario;

    @BeforeEach
    void setup() {
        var auth = authService.register(
            new RegisterRequest("João", "joao@links.com", "Senha@123")
        );
        usuario = new User();
        usuario.setId(auth.user().id());
        usuario.setEmail(auth.user().email());
        usuario.setName("João");
        usuario.setPlan(User.Plan.FREE);
    }

    @Test
    @DisplayName("Deve criar link com slug automático")
    void deveCriarLinkComSlugAutomatico() {
        var request = new LinkRequest(
            "https://mercadolivre.com.br/airfryer",
            "Airfryer Philips", null, "Janeiro 2025", null, null
        );

        var response = linkService.criar(request, usuario);

        assertThat(response.id()).isNotNull();
        assertThat(response.slug()).startsWith("joao/");
        assertThat(response.shortUrl()).contains("/r/");
        assertThat(response.qrCodeUrl()).contains("qrserver.com");
        assertThat(response.clicks()).isZero();
    }

    @Test
    @DisplayName("Deve criar link com slug personalizado")
    void deveCriarLinkComSlugPersonalizado() {
        var request = new LinkRequest(
            "https://shopee.com.br/produto",
            "Produto Shopee", "meu-produto", null, null, null
        );

        var response = linkService.criar(request, usuario);
        assertThat(response.slug()).isEqualTo("joao/meu-produto");
    }

    @Test
    @DisplayName("Não deve aceitar slug duplicado")
    void naoDeveAceitarSlugDuplicado() {
        var request = new LinkRequest(
            "https://mercadolivre.com.br/p1",
            "Produto 1", "meu-slug", null, null, null
        );
        linkService.criar(request, usuario);

        var request2 = new LinkRequest(
            "https://mercadolivre.com.br/p2",
            "Produto 2", "meu-slug", null, null, null
        );
        assertThatThrownBy(() -> linkService.criar(request2, usuario))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("slug já está em uso");
    }

    @Test
    @DisplayName("Deve bloquear após atingir limite do plano FREE")
    void deveBloquearAoAtingirLimiteFree() {
        for (int i = 0; i < 10; i++) {
            linkService.criar(new LinkRequest(
                "https://mercadolivre.com.br/p" + i,
                "Produto " + i, null, null, null, null
            ), usuario);
        }

        assertThatThrownBy(() -> linkService.criar(new LinkRequest(
            "https://mercadolivre.com.br/p11",
            "Produto 11", null, null, null, null
        ), usuario))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Limite de 10 links");
    }

    @Test
    @DisplayName("Deve desativar link")
    void deveDesativarLink() {
        var link = linkService.criar(new LinkRequest(
            "https://mercadolivre.com.br/produto",
            "Produto", null, null, null, null
        ), usuario);

        linkService.desativar(link.id(), usuario);

        assertThat(linkRepository.findBySlugAndActiveTrue(link.slug())).isEmpty();
    }
}
