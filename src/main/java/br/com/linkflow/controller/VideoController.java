package br.com.linkflow.controller;

import br.com.linkflow.dto.response.VideoJobResponse;
import br.com.linkflow.entity.User;
import br.com.linkflow.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    // POST /api/videos — inicia pipeline a partir de um roteiro
    @PostMapping
    public ResponseEntity<VideoJobResponse> iniciar(
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal User user
    ) {
        UUID scriptId = UUID.fromString(body.get("scriptId"));
        String avatarId = body.get("avatarId"); // opcional
        String voiceId  = body.get("voiceId");  // opcional

        return ResponseEntity
            .status(HttpStatus.ACCEPTED) // 202 — processamento assíncrono
            .body(videoService.iniciar(scriptId, avatarId, voiceId, user));
    }

    // GET /api/videos/{id} — status do job (polling pelo frontend)
    @GetMapping("/{id}")
    public ResponseEntity<VideoJobResponse> status(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(videoService.buscarPorId(id, user));
    }

    // GET /api/videos — histórico de vídeos do usuário
    @GetMapping
    public ResponseEntity<Page<VideoJobResponse>> listar(
        @RequestParam(defaultValue = "0") int page,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(videoService.listar(user, page));
    }
}
