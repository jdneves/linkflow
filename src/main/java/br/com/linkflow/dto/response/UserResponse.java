package br.com.linkflow.dto.response;

import br.com.linkflow.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String name,
    String email,
    String plan,
    String avatarUrl,
    Boolean active,
    LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getPlan().name(),
            user.getAvatarUrl(),
            user.getActive(),
            user.getCreatedAt()
        );
    }
}
