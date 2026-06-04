package br.com.linkflow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "scripts")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Script {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name", nullable = false, length = 500)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Format format;

    @Column(nullable = false, length = 50)
    private String tone;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String hook;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> topics;

    @Column(columnDefinition = "TEXT")
    private String cta;

    @Column(columnDefinition = "TEXT")
    private String caption;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> hashtags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> stories;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Platform { YOUTUBE, INSTAGRAM, TIKTOK }

    public enum Format {
        REVIEW, UNBOXING, VALE_A_PENA, COMPARATIVO, DICAS_DE_USO;

        public String toPortugues() {
            return switch (this) {
                case REVIEW       -> "review completo";
                case UNBOXING     -> "unboxing";
                case VALE_A_PENA  -> "vale a pena comprar?";
                case COMPARATIVO  -> "comparativo";
                case DICAS_DE_USO -> "dicas de uso";
            };
        }
    }
}
