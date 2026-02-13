package io.github.yerektus.aitryon.domain;

import io.github.yerektus.aitryon.common.BadRequestException;

import java.util.Locale;

public enum SocialLookVisibility {
    PUBLIC,
    FOLLOWERS,
    PRIVATE;

    public String toApiValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SocialLookVisibility fromApiValue(String value) {
        if (value == null) {
            throw new BadRequestException("visibility is required");
        }

        final String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "public" -> PUBLIC;
            case "followers" -> FOLLOWERS;
            case "private" -> PRIVATE;
            default -> throw new BadRequestException("visibility must be public, followers or private");
        };
    }
}
