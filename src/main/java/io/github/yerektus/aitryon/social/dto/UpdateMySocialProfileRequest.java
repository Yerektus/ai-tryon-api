package io.github.yerektus.aitryon.social.dto;

import jakarta.validation.constraints.Size;

public record UpdateMySocialProfileRequest(
        @Size(max = 120, message = "displayName too long")
        String displayName,

        @Size(max = 500, message = "bio too long")
        String bio,

        @Size(max = 2048, message = "avatarUrl too long")
        String avatarUrl
) {
}
