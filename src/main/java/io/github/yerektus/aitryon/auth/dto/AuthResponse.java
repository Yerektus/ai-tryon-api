package io.github.yerektus.aitryon.auth.dto;

public record AuthResponse(
        UserResponse user,
        String accessToken,
        String refreshToken,
        long accessExpiresIn
) {
}
