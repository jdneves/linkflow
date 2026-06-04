package br.com.linkflow.repository;

import br.com.linkflow.entity.Script;
import br.com.linkflow.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ScriptRepository extends JpaRepository<Script, UUID> {

    Page<Script> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    @Query("SELECT COUNT(s) FROM Script s WHERE s.user = :user AND MONTH(s.createdAt) = MONTH(CURRENT_DATE) AND YEAR(s.createdAt) = YEAR(CURRENT_DATE)")
    long countByUserThisMonth(@Param("user") User user);

    Page<Script> findByUserAndProductIdOrderByCreatedAtDesc(User user, UUID productId, Pageable pageable);
}
