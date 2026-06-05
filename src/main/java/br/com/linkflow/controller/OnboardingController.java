package br.com.linkflow.controller;

import br.com.linkflow.dto.response.OnboardingResponse;
import br.com.linkflow.entity.User;
import br.com.linkflow.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    // GET /api/onboarding — progresso atual do João
    @GetMapping
    public ResponseEntity<OnboardingResponse> consultar(
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(onboardingService.consultar(user));
    }
}
