package io.github.yerektus.aitryon.social.dto;

import java.util.UUID;

public record SocialUserResponse(
        UUID id,
        String username,
        String displayName,
        String avatarUrl
) {
}
