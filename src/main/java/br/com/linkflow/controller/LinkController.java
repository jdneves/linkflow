package br.com.linkflow.controller;

import br.com.linkflow.dto.request.LinkRequest;
import br.com.linkflow.dto.response.LinkResponse;
import br.com.linkflow.entity.User;
import br.com.linkflow.service.LinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class LinkController {

    private final LinkService linkService;

    // ── Gestão de links (autenticado) ─────────────────────────────────────

    @PostMapping("/api/links")
    public ResponseEntity<LinkResponse> criar(
        @Valid @RequestBody LinkRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(linkService.criar(request, user));
    }

    @GetMapping("/api/links")
    public ResponseEntity<Page<LinkResponse>> listar(
        @RequestParam(defaultValue = "0") int page,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(linkService.listar(user, page));
    }

    @GetMapping("/api/links/{id}")
    public ResponseEntity<LinkResponse> buscarPorId(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(linkService.buscarPorId(id, user));
    }

    @DeleteMapping("/api/links/{id}")
    public ResponseEntity<Void> desativar(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        linkService.desativar(id, user);
        return ResponseEntity.noContent().build();
    }

    // ── Redirect público (sem autenticação) ───────────────────────────────
    // Ex: GET /r/joao/airfryer-philips → redireciona para ML/Shopee

    @GetMapping("/r/{username}/{slug}")
    public ResponseEntity<Void> redirecionar(
        @PathVariable String username,
        @PathVariable String slug,
        HttpServletRequest request
    ) {
        String fullSlug = username + "/" + slug;
        String destino  = linkService.redirecionar(fullSlug, request);

        return ResponseEntity
            .status(HttpStatus.FOUND) // 302
            .header(HttpHeaders.LOCATION, destino)
            .build();
    }
}
