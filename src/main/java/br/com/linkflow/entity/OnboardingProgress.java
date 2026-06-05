package br.com.linkflow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "onboarding_progress")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class OnboardingProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Cada passo: true = concluído
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Boolean> steps = new LinkedHashMap<>(Map.of(
        "cadastro_concluido",    true,  // já concluído no registro
        "primeiro_produto",      false, // acessou o Radar
        "primeiro_roteiro",      false, // gerou o primeiro roteiro
        "primeiro_link",         false, // criou o primeiro link
        "primeiro_video",        false  // gerou o primeiro vídeo
    ));

    @Builder.Default
    private Boolean completed = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public int calcularProgresso() {
        long concluidos = steps.values().stream().filter(v -> v).count();
        return (int) Math.round((double) concluidos / steps.size() * 100);
    }

    public void concluirPasso(String passo) {
        if (steps.containsKey(passo)) {
            steps.put(passo, true);
            if (steps.values().stream().allMatch(v -> v)) {
                this.completed = true;
                this.completedAt = LocalDateTime.now();
            }
        }
    }
}
