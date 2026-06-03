package br.com.linkflow.dto.response;

import br.com.linkflow.entity.User;

import java.util.UUID;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    UserInfo user
) {
    public record UserInfo(
        UUID id,
        String name,
        String email,
        String plan
    ) {
        public static UserInfo from(User user) {
            return new UserInfo(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPlan().name()
            );
        }
    }
}
