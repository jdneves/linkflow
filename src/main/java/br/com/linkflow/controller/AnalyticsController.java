package br.com.linkflow.controller;

import br.com.linkflow.dto.response.DashboardResponse;
import br.com.linkflow.entity.User;
import br.com.linkflow.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    // GET /api/analytics/dashboard — dados completos do dashboard
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard(
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(analyticsService.dashboard(user));
    }

    // POST /api/analytics/relatorio — dispara o relatório semanal manualmente
    @PostMapping("/relatorio")
    public ResponseEntity<Void> dispararRelatorio(
        @AuthenticationPrincipal User user
    ) {
        analyticsService.enviarRelatorio(user);
        return ResponseEntity.accepted().build();
    }
}
