package br.com.linkflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clicks", indexes = {
    @Index(name = "idx_clicks_link_id",    columnList = "link_id"),
    @Index(name = "idx_clicks_created_at", columnList = "created_at")
})
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Click {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_id", nullable = false)
    private AffiliateLink link;

    // Origem do clique
    @Column(name = "referer", length = 500)
    private String referer;

    // Plataforma detectada pelo user-agent
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Device device = Device.UNKNOWN;

    // IP anonimizado (apenas os 3 primeiros octetos: 192.168.1.xxx)
    @Column(name = "ip_hash", length = 50)
    private String ipHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Device { MOBILE, DESKTOP, TABLET, UNKNOWN }
}
