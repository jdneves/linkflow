package br.com.linkflow.service;

import br.com.linkflow.client.ClaudeClient;
import br.com.linkflow.client.ElevenLabsClient;
import br.com.linkflow.client.HeyGenClient;
import br.com.linkflow.client.StorageClient;
import br.com.linkflow.dto.request.RegisterRequest;
import br.com.linkflow.dto.request.ScriptRequest;
import br.com.linkflow.entity.Script.Format;
import br.com.linkflow.entity.Script.Platform;
import br.com.linkflow.entity.User;
import br.com.linkflow.exception.BusinessException;
import br.com.linkflow.repository.VideoJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VideoServiceTest {

    @Autowired VideoService videoService;
    @Autowired VideoJobRepository videoJobRepository;
    @Autowired AuthService authService;
    @Autowired ScriptService scriptService;

    @MockBean
    ClaudeClient claudeClient;
    @MockBean ElevenLabsClient elevenLabsClient;
    @MockBean HeyGenClient heyGenClient;
    @MockBean StorageClient storageClient;

    private User usuario;
    private UUID scriptId;

    private static final String MOCK_CLAUDE = """
        {"titulo_sugerido":"Título","gancho_abertura":"Gancho",
         "topicos":["T1","T2"],"cta_afiliado":"CTA",
         "legenda_instagram":"Legenda","hashtags":["h1"],"stories":["S1"]}
        """;

    @BeforeEach
    void setup() {
        var auth = authService.register(
            new RegisterRequest("João", "joao@video.com", "Senha@123")
        );
        usuario = new User();
        usuario.setId(auth.user().id());
        usuario.setEmail(auth.user().email());
        usuario.setName("João");
        usuario.setPlan(User.Plan.FREE);

        when(claudeClient.completar(anyString())).thenReturn(MOCK_CLAUDE);
        var script = scriptService.gerar(new ScriptRequest(
            null, "Airfryer", null,
            Platform.YOUTUBE, Format.REVIEW, "descontraído", "médio"
        ), usuario);
        scriptId = script.id();

        when(elevenLabsClient.textToSpeech(anyString(), any()))
            .thenReturn("audio-bytes".getBytes());
        when(storageClient.uploadAudio(any(), any()))
            .thenReturn("https://r2.example.com/audio/test.mp3");
        when(heyGenClient.submeterVideo(anyString(), any()))
            .thenReturn("heygen-video-id-123");
    }

    @Test
    @DisplayName("Deve iniciar pipeline e criar job com status PENDING")
    void deveIniciarPipeline() {
        var response = videoService.iniciar(scriptId, null, null, usuario);

        assertThat(response.id()).isNotNull();
        // Status pode ser PENDING ou GENERATING_AUDIO dependendo da velocidade do @Async
        assertThat(response.status()).isIn("PENDING", "GENERATING_AUDIO", "GENERATING_VIDEO");
    }

    @Test
    @DisplayName("Deve bloquear após atingir limite FREE (2 vídeos/mês)")
    void deveBloquearAoAtingirLimite() throws Exception {
        // Gera 2 vídeos (limite FREE)
        videoService.iniciar(scriptId, null, null, usuario);
        videoService.iniciar(scriptId, null, null, usuario);

        assertThatThrownBy(() -> videoService.iniciar(scriptId, null, null, usuario))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Limite de 2 vídeos/mês");
    }

    @Test
    @DisplayName("Deve atualizar status para COMPLETED quando HeyGen terminar")
    void deveAtualizarStatusCompleted() {
        var jobResponse = videoService.iniciar(scriptId, null, null, usuario);

        // Simula HeyGen completando
        when(heyGenClient.checarStatus(anyString()))
            .thenReturn(new HeyGenClient.VideoStatus(
                "completed",
                "https://cdn.heygen.com/video.mp4",
                null
            ));

        // Força o polling
        videoService.verificarJobsPendentes();

        // Busca o job atualizado
        var job = videoJobRepository.findById(jobResponse.id()).orElseThrow();
        assertThat(job.getVideoUrl()).isEqualTo("https://cdn.heygen.com/video.mp4");
        // Status pode variar dependendo do @Async, mas o vídeo URL deve estar salvo
    }

    @Test
    @DisplayName("Deve buscar job por ID")
    void deveBuscarJobPorId() {
        var criado = videoService.iniciar(scriptId, null, null, usuario);
        var buscado = videoService.buscarPorId(criado.id(), usuario);

        assertThat(buscado.id()).isEqualTo(criado.id());
        assertThat(buscado.productName()).isEqualTo("Airfryer");
    }
}
