package br.com.linkflow.controller;

import br.com.linkflow.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Endpoints de operação (ops). Protegidos por um token compartilhado no header
 * {@code X-Admin-Token}, comparado contra {@code linkflow.admin.token}
 * ({@code ADMIN_API_TOKEN}). Independem do JWT — pensados para curl/cron.
 *
 * <p>Se o token não estiver configurado, o endpoint responde 503 (desabilitado),
 * para nunca ficar aberto por esquecimento de configuração.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;

    @Value("${linkflow.admin.token:}")
    private String adminToken;

    /**
     * Dispara o sync de produtos sob demanda (mesmo fluxo do cron de 6h):
     * provider real por plataforma quando habilitado, com fallback para mock.
     * Útil para puxar dados reais sem depender do banco vazio nem do cron.
     */
    @PostMapping("/products/sync")
    public ResponseEntity<Map<String, Object>> syncProducts(
        @RequestHeader(value = "X-Admin-Token", required = false) String token
    ) {
        if (adminToken == null || adminToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Endpoint admin desabilitado: defina ADMIN_API_TOKEN."));
        }
        if (token == null || !secureEquals(adminToken, token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Token admin inválido."));
        }

        log.info("Sync manual de produtos disparado via /api/admin/products/sync.");
        int total = productService.sincronizarProdutos();
        return ResponseEntity.ok(Map.of("status", "ok", "produtosProcessados", total));
    }

    /** Comparação em tempo constante para não vazar o token por timing. */
    private boolean secureEquals(String expected, String provided) {
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            provided.getBytes(StandardCharsets.UTF_8)
        );
    }
}
