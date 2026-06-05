package br.com.linkflow.service;

import br.com.linkflow.dto.request.RegisterRequest;
import br.com.linkflow.entity.User;
import br.com.linkflow.repository.OnboardingRepository;
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
class OnboardingServiceTest {

    @Autowired OnboardingService onboardingService;
    @Autowired OnboardingRepository onboardingRepository;
    @Autowired AuthService authService;

    private User usuario;

    @BeforeEach
    void setup() {
        var auth = authService.register(
            new RegisterRequest("João", "joao@onboarding.com", "Senha@123")
        );
        usuario = new User();
        usuario.setId(auth.user().id());
        usuario.setEmail(auth.user().email());
        usuario.setName("João");
        usuario.setPlan(User.Plan.FREE);
    }

    @Test
    @DisplayName("Deve inicializar onboarding com cadastro já concluído")
    void deveInicializarOnboarding() {
        onboardingService.inicializar(usuario);
        var response = onboardingService.consultar(usuario);

        assertThat(response.progresso()).isGreaterThan(0);
        var cadastro = response.passos().stream()
            .filter(p -> p.id().equals("cadastro_concluido"))
            .findFirst().orElseThrow();
        assertThat(cadastro.concluido()).isTrue();
    }

    @Test
    @DisplayName("Deve avançar progresso ao concluir passo")
    void deveAvancarProgresso() {
        onboardingService.inicializar(usuario);
        int progressoInicial = onboardingService.consultar(usuario).progresso();

        onboardingService.concluirPasso(usuario, OnboardingService.PASSO_PRODUTO);
        int progressoAtual = onboardingService.consultar(usuario).progresso();

        assertThat(progressoAtual).isGreaterThan(progressoInicial);
    }

    @Test
    @DisplayName("Não deve regredir ao concluir passo já concluído")
    void naoDeveRegredir() {
        onboardingService.inicializar(usuario);
        onboardingService.concluirPasso(usuario, OnboardingService.PASSO_ROTEIRO);
        int progressoA = onboardingService.consultar(usuario).progresso();

        onboardingService.concluirPasso(usuario, OnboardingService.PASSO_ROTEIRO);
        int progressoB = onboardingService.consultar(usuario).progresso();

        assertThat(progressoB).isEqualTo(progressoA);
    }

    @Test
    @DisplayName("Deve marcar completed ao concluir todos os passos")
    void deveConcluirOnboarding() {
        onboardingService.inicializar(usuario);
        onboardingService.concluirPasso(usuario, OnboardingService.PASSO_PRODUTO);
        onboardingService.concluirPasso(usuario, OnboardingService.PASSO_ROTEIRO);
        onboardingService.concluirPasso(usuario, OnboardingService.PASSO_LINK);
        onboardingService.concluirPasso(usuario, OnboardingService.PASSO_VIDEO);

        var response = onboardingService.consultar(usuario);
        assertThat(response.progresso()).isEqualTo(100);
        assertThat(response.completed()).isTrue();
        assertThat(response.mensagemMotivacional()).contains("Parabéns");
    }

    @Test
    @DisplayName("Deve retornar próximo passo pendente corretamente")
    void deveRetornarProximoPasso() {
        onboardingService.inicializar(usuario);
        onboardingService.concluirPasso(usuario, OnboardingService.PASSO_PRODUTO);

        var response = onboardingService.consultar(usuario);
        // próximo passo após produto é roteiro
        assertThat(response.proximoPasso()).isEqualTo("/studio");
    }
}
