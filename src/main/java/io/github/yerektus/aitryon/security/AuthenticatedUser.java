package io.github.yerektus.aitryon.security;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String email
) {
}
