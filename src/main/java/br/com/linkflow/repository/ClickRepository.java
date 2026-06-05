package br.com.linkflow.repository;

import br.com.linkflow.entity.Click;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ClickRepository extends JpaRepository<Click, UUID> {

    long countByLinkId(UUID linkId);

    // Cliques por dia nos últimos N dias (para gráfico)
    @Query("""
        SELECT CAST(c.createdAt AS date) AS dia, COUNT(c) AS total
        FROM Click c
        WHERE c.link.user.id = :userId
          AND c.createdAt >= :desde
        GROUP BY CAST(c.createdAt AS date)
        ORDER BY dia ASC
    """)
    List<Object[]> clicksPorDia(@Param("userId") UUID userId,
                                @Param("desde") LocalDateTime desde);

    // Cliques por device
    @Query("""
        SELECT c.device, COUNT(c)
        FROM Click c
        WHERE c.link.user.id = :userId
          AND c.createdAt >= :desde
        GROUP BY c.device
    """)
    List<Object[]> clicksPorDevice(@Param("userId") UUID userId,
                                   @Param("desde") LocalDateTime desde);

    // Total de cliques do usuário no período
    @Query("""
        SELECT COUNT(c) FROM Click c
        WHERE c.link.user.id = :userId
          AND c.createdAt >= :desde
    """)
    long totalClicksUsuario(@Param("userId") UUID userId,
                            @Param("desde") LocalDateTime desde);
}
