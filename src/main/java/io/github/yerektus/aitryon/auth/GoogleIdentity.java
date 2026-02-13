package io.github.yerektus.aitryon.auth;

public record GoogleIdentity(
        String sub,
        String email,
        String displayName
) {
}
