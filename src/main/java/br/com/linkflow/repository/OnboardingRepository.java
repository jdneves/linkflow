package br.com.linkflow.repository;

import br.com.linkflow.entity.OnboardingProgress;
import br.com.linkflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OnboardingRepository extends JpaRepository<OnboardingProgress, UUID> {
    Optional<OnboardingProgress> findByUser(User user);
}
