package br.com.linkflow.service;

import br.com.linkflow.entity.User;
import br.com.linkflow.exception.BusinessException;
import br.com.linkflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User findById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Usuário não encontrado."));
    }

    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new BusinessException("Usuário não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<User> search(String plan, Boolean active) {
        // Busca com filtros opcionais
        if (plan != null && active != null) {
            return userRepository.findByPlanAndActive(User.Plan.valueOf(plan.toUpperCase()), active);
        } else if (plan != null) {
            return userRepository.findByPlan(User.Plan.valueOf(plan.toUpperCase()));
        } else if (active != null) {
            return userRepository.findByActive(active);
        } else {
            return userRepository.findAll();
        }
    }

    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<User> findByPlan(String plan) {
        return userRepository.findByPlan(User.Plan.valueOf(plan.toUpperCase()));
    }
}
