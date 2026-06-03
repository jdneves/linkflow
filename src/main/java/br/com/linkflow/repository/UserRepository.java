package br.com.linkflow.repository;

import br.com.linkflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // Métodos de busca adicionais
    List<User> findByPlan(User.Plan plan);
    List<User> findByActive(Boolean active);
    List<User> findByPlanAndActive(User.Plan plan, Boolean active);
}
