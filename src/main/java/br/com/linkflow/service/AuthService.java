package br.com.linkflow.service;

import br.com.linkflow.dto.request.LoginRequest;
import br.com.linkflow.dto.request.RegisterRequest;
import br.com.linkflow.dto.response.AuthResponse;
import br.com.linkflow.entity.RefreshToken;
import br.com.linkflow.entity.User;
import br.com.linkflow.exception.BusinessException;
import br.com.linkflow.repository.RefreshTokenRepository;
import br.com.linkflow.repository.UserRepository;
import br.com.linkflow.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Value("${linkflow.jwt.refresh-expiration}")
    private long refreshExpiration;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("E-mail já cadastrado.");
        }

        var user = User.builder()
            .name(request.name())
            .email(request.email().toLowerCase())
            .password(passwordEncoder.encode(request.password()))
            .build();

        userRepository.save(user);
        log.info("Novo usuário cadastrado: {}", user.getEmail());

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        var user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new BusinessException("Usuário não encontrado."));

        // Remove tokens antigos do usuário
        refreshTokenRepository.deleteByUser(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        var token = refreshTokenRepository.findByToken(refreshToken)
            .orElseThrow(() -> new BusinessException("Refresh token inválido."));

        if (token.isExpired()) {
            refreshTokenRepository.delete(token);
            throw new BusinessException("Refresh token expirado. Faça login novamente.");
        }

        var user = token.getUser();
        refreshTokenRepository.delete(token);

        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(User user) {
        refreshTokenRepository.deleteByUser(user);
        log.info("Logout: {}", user.getEmail());
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = UUID.randomUUID().toString();

        var tokenEntity = RefreshToken.builder()
            .user(user)
            .token(refreshToken)
            .expiresAt(LocalDateTime.now().plusSeconds(refreshExpiration / 1000))
            .build();

        refreshTokenRepository.save(tokenEntity);

        return new AuthResponse(
            accessToken,
            refreshToken,
            AuthResponse.UserInfo.from(user)
        );
    }
}
