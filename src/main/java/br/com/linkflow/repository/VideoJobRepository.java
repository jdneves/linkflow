package br.com.linkflow.repository;

import br.com.linkflow.entity.VideoJob;
import br.com.linkflow.entity.VideoJob.Status;
import br.com.linkflow.entity.VideoMode;
import br.com.linkflow.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VideoJobRepository extends JpaRepository<VideoJob, UUID> {

    Page<VideoJob> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    // Jobs pendentes para o scheduler de polling
    List<VideoJob> findByStatusIn(List<Status> statuses);

    @Query("SELECT COUNT(v) FROM VideoJob v WHERE v.user = :user AND MONTH(v.createdAt) = MONTH(CURRENT_DATE) AND YEAR(v.createdAt) = YEAR(CURRENT_DATE)")
    long countByUserThisMonth(@Param("user") User user);

    @Query("SELECT COUNT(v) FROM VideoJob v WHERE v.user = :user AND v.mode = :mode AND MONTH(v.createdAt) = MONTH(CURRENT_DATE) AND YEAR(v.createdAt) = YEAR(CURRENT_DATE)")
    long countByUserAndModeThisMonth(@Param("user") User user, @Param("mode") VideoMode mode);
}
