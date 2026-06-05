package br.com.linkflow.repository;

import br.com.linkflow.entity.AffiliateLink;
import br.com.linkflow.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AffiliateLinkRepository extends JpaRepository<AffiliateLink, UUID> {

    Optional<AffiliateLink> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    Page<AffiliateLink> findByUserAndActiveTrueOrderByCreatedAtDesc(User user, Pageable pageable);

    @Query("SELECT COUNT(l) FROM AffiliateLink l WHERE l.user = :user AND l.active = true")
    long countActiveByUser(@Param("user") User user);

    // Top links por cliques para o dashboard
    @Query("""
        SELECT l FROM AffiliateLink l
        WHERE l.user = :user AND l.active = true
        ORDER BY l.clicks DESC
    """)
    Page<AffiliateLink> findTopByUser(@Param("user") User user, Pageable pageable);
}
