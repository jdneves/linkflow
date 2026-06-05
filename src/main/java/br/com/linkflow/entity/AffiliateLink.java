package br.com.linkflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "affiliate_links", indexes = {
    @Index(name = "idx_links_slug",    columnList = "slug",    unique = true),
    @Index(name = "idx_links_user_id", columnList = "user_id")
})
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class AffiliateLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id")
    private Script script;

    // Slug amigável: ex. "joao/airfryer-philips"
    @Column(nullable = false, unique = true, length = 200)
    private String slug;

    // URL original do afiliado (ML, Shopee, etc)
    @Column(name = "destination_url", nullable = false, length = 1000)
    private String destinationUrl;

    @Column(length = 200)
    private String title;

    @Column(length = 100)
    private String campaign; // ex: "Janeiro 2025", "Black Friday"

    @Builder.Default
    private Long clicks = 0L;

    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_click_at")
    private LocalDateTime lastClickAt;
}
