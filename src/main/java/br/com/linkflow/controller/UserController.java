package br.com.linkflow.controller;

import br.com.linkflow.dto.response.UserResponse;
import br.com.linkflow.entity.User;
import br.com.linkflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * GET /api/users/{id}
     * Busca usuário por ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PRO')") // Apenas usuários PRO podem buscar outros usuários
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        var user = userService.findById(id);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * GET /api/users?email=teste@email.com
     * Busca usuário por email (query parameter)
     */
    @GetMapping
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<UserResponse> getUserByEmail(@RequestParam String email) {
        var user = userService.findByEmail(email);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * GET /api/users/search?plan=FREE&active=true
     * Busca com múltiplos filtros opcionais
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<List<UserResponse>> searchUsers(
        @RequestParam(required = false) String plan,
        @RequestParam(required = false) Boolean active
    ) {
        var users = userService.search(plan, active);
        return ResponseEntity.ok(users.stream().map(UserResponse::from).toList());
    }

    /**
     * GET /api/users/all?page=0&size=10
     * Lista todos com paginação
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<Page<UserResponse>> listUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        var users = userService.findAll(PageRequest.of(page, size));
        return ResponseEntity.ok(users.map(UserResponse::from));
    }

    /**
     * GET /api/users/me
     * Retorna o próprio usuário logado (já existe no AuthController, mas pode duplicar aqui)
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * GET /api/users/plan/{plan}
     * Busca usuários por plano (path variable)
     */
    @GetMapping("/plan/{plan}")
    @PreAuthorize("hasRole('PRO')")
    public ResponseEntity<List<UserResponse>> getUsersByPlan(@PathVariable String plan) {
        var users = userService.findByPlan(plan);
        return ResponseEntity.ok(users.stream().map(UserResponse::from).toList());
    }
}
