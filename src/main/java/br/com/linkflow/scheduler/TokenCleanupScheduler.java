package br.com.linkflow.scheduler;

import br.com.linkflow.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    // Executa toda madrugada às 3h
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void limparTokensExpirados() {
        log.info("Iniciando limpeza de refresh tokens expirados...");
        refreshTokenRepository.deleteAllExpired();
        log.info("Limpeza de tokens concluída.");
    }
}
