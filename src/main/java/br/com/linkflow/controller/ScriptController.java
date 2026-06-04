package br.com.linkflow.controller;

import br.com.linkflow.dto.request.ScriptRequest;
import br.com.linkflow.dto.response.ScriptResponse;
import br.com.linkflow.entity.User;
import br.com.linkflow.service.ScriptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/studio")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;

    // POST /api/studio/roteiro — gera e salva um novo roteiro
    @PostMapping("/roteiro")
    public ResponseEntity<ScriptResponse> gerar(
        @Valid @RequestBody ScriptRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(scriptService.gerar(request, user));
    }

    // GET /api/studio/roteiros — histórico do usuário
    @GetMapping("/roteiros")
    public ResponseEntity<Page<ScriptResponse>> listar(
        @RequestParam(defaultValue = "0") int page,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(scriptService.listar(user, page));
    }

    // GET /api/studio/roteiros/{id}
    @GetMapping("/roteiros/{id}")
    public ResponseEntity<ScriptResponse> buscarPorId(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(scriptService.buscarPorId(id, user));
    }

    // GET /api/studio/roteiros/produto/{productId} — roteiros de um produto específico
    @GetMapping("/roteiros/produto/{productId}")
    public ResponseEntity<Page<ScriptResponse>> listarPorProduto(
        @PathVariable UUID productId,
        @RequestParam(defaultValue = "0") int page,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(scriptService.listarPorProduto(productId, user, page));
    }
}
