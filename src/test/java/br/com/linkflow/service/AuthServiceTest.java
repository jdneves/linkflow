package br.com.linkflow.service;

import br.com.linkflow.dto.request.LoginRequest;
import br.com.linkflow.dto.request.RegisterRequest;
import br.com.linkflow.exception.BusinessException;
import br.com.linkflow.repository.RefreshTokenRepository;
import br.com.linkflow.repository.UserRepository;
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
class AuthServiceTest {

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("Deve registrar novo usuário com sucesso")
    void deveRegistrarUsuario() {
        var request = new RegisterRequest("João Silva", "joao@teste.com", "Senha@123");
        var response = authService.register(request);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("joao@teste.com");
        assertThat(response.user().plan()).isEqualTo("FREE");
    }

    @Test
    @DisplayName("Não deve registrar e-mail duplicado")
    void naoDeveRegistrarEmailDuplicado() {
        var request = new RegisterRequest("João Silva", "joao@teste.com", "Senha@123");
        authService.register(request);

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(BusinessException.class)
            .hasMessage("E-mail já cadastrado.");
    }

    @Test
    @DisplayName("Deve fazer login e retornar tokens")
    void deveFazerLogin() {
        authService.register(new RegisterRequest("João", "joao2@teste.com", "Senha@123"));
        var response = authService.login(new LoginRequest("joao2@teste.com", "Senha@123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("Deve renovar token com refresh token válido")
    void deveRenovarToken() {
        authService.register(new RegisterRequest("João", "joao3@teste.com", "Senha@123"));
        var login = authService.login(new LoginRequest("joao3@teste.com", "Senha@123"));
        var refresh = authService.refresh(login.refreshToken());

        assertThat(refresh.accessToken()).isNotBlank();
        assertThat(refresh.accessToken()).isNotEqualTo(login.accessToken());
    }

    @Test
    @DisplayName("Deve rejeitar refresh token inválido")
    void deveRejeitarRefreshTokenInvalido() {
        assertThatThrownBy(() -> authService.refresh("token-invalido"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Refresh token inválido.");
    }
}
