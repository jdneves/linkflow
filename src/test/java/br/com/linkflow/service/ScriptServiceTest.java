package br.com.linkflow.service;

import br.com.linkflow.client.ClaudeClient;
import br.com.linkflow.dto.request.ScriptRequest;
import br.com.linkflow.entity.Script.Format;
import br.com.linkflow.entity.Script.Platform;
import br.com.linkflow.entity.User;
import br.com.linkflow.exception.BusinessException;
import br.com.linkflow.repository.ScriptRepository;
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
class ScriptServiceTest {

    @Autowired ScriptService scriptService;
    @Autowired ScriptRepository scriptRepository;
    @Autowired AuthService authService;
    @MockBean  ClaudeClient claudeClient;

    private User usuario;

    private static final String MOCK_RESPOSTA_CLAUDE = """
        {
          "titulo_sugerido": "Airfryer Philips: Vale a pena em 2025?",
          "gancho_abertura": "Você já gastou dinheiro em algo que prometia muito e entregou pouco? Hoje eu vou te contar a verdade sobre a Airfryer Philips depois de 30 dias de uso.",
          "topicos": ["Unboxing e primeiras impressões", "Testando a temperatura e o timer", "Frango, batata e pipoca: resultados reais", "Consumo de energia vs fritadeira comum", "Vale a pena o preço?"],
          "cta_afiliado": "Se você quiser aproveitar a promoção que encontrei, deixei o link direto na descrição do vídeo — tá com um desconto bacana hoje.",
          "legenda_instagram": "🍟 Testei a Airfryer Philips por 30 dias e vou te contar a verdade! Será que vale cada centavo? Assiste o vídeo completo e me conta o que achou nos comentários! 👇",
          "hashtags": ["airfryer", "airfryerphilips", "dicasdelardoce", "cozinhasaudavel", "fritadeirasemoleo", "receitasrapidas", "afiliadodigital", "produtosquevaloram"],
          "stories": ["Slide 1: Você sabia que dá pra fazer frango crocante SEM UMA GOTA de óleo?", "Slide 2: Testei a Airfryer Philips 4.1L por 30 dias", "Slide 3: O resultado me surpreendeu muito...", "Slide 4: Link do produto com desconto na bio! 🔗", "Slide 5: Comenta aqui: você já tem airfryer? 👇"]
        }
        """;

    @BeforeEach
    void setup() {
        var auth = authService.register(
            new br.com.linkflow.dto.request.RegisterRequest("João", "joao@studio.com", "Senha@123")
        );
        usuario = new User();
        usuario.setId(auth.user().id());
        usuario.setEmail(auth.user().email());
        usuario.setPlan(User.Plan.FREE);
    }

    @Test
    @DisplayName("Deve gerar e salvar roteiro com sucesso")
    void deveGerarRoteiro() {
        when(claudeClient.completar(anyString())).thenReturn(MOCK_RESPOSTA_CLAUDE);

        var request = new ScriptRequest(
            null, "Airfryer Philips 4.1L", "Timer digital, 7 programas",
            Platform.YOUTUBE, Format.REVIEW, "descontraído", "médio (5-10 min)"
        );

        var response = scriptService.gerar(request, usuario);

        assertThat(response.id()).isNotNull();
        assertThat(response.title()).isEqualTo("Airfryer Philips: Vale a pena em 2025?");
        assertThat(response.topics()).hasSize(5);
        assertThat(response.hashtags()).hasSize(8);
        assertThat(response.stories()).hasSize(5);
        assertThat(response.platform()).isEqualTo("YOUTUBE");
    }

    @Test
    @DisplayName("Deve persistir roteiro no banco")
    void devePersistirRoteiro() {
        when(claudeClient.completar(anyString())).thenReturn(MOCK_RESPOSTA_CLAUDE);

        var request = new ScriptRequest(
            null, "Airfryer Philips 4.1L", null,
            Platform.INSTAGRAM, Format.VALE_A_PENA, "entusiasmado", "curto (até 3 min)"
        );

        scriptService.gerar(request, usuario);
        assertThat(scriptRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Deve bloquear após atingir limite mensal do plano FREE")
    void deveBlocarAoAtingirLimite() {
        when(claudeClient.completar(anyString())).thenReturn(MOCK_RESPOSTA_CLAUDE);

        var request = new ScriptRequest(
            null, "Produto Teste", null,
            Platform.YOUTUBE, Format.REVIEW, "simples", "curto (até 3 min)"
        );

        // Gera 5 roteiros (limite FREE)
        for (int i = 0; i < 5; i++) {
            scriptService.gerar(request, usuario);
        }

        // O 6º deve lançar exceção
        assertThatThrownBy(() -> scriptService.gerar(request, usuario))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Limite de 5 roteiros/mês atingido");
    }

    @Test
    @DisplayName("Deve listar roteiros do usuário paginados")
    void deveListarRoteiros() {
        when(claudeClient.completar(anyString())).thenReturn(MOCK_RESPOSTA_CLAUDE);

        var request = new ScriptRequest(
            null, "Produto Teste", null,
            Platform.TIKTOK, Format.UNBOXING, "entusiasmado", "curto (até 3 min)"
        );

        scriptService.gerar(request, usuario);
        scriptService.gerar(request, usuario);

        var pagina = scriptService.listar(usuario, 0);
        assertThat(pagina.getTotalElements()).isEqualTo(2);
    }
}
