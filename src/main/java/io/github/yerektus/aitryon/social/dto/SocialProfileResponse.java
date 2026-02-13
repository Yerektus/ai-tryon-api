package io.github.yerektus.aitryon.social.dto;

import java.util.UUID;

public record SocialProfileResponse(
        UUID id,
        String username,
        String displayName,
        String avatarUrl,
        String bio,
        long followersCount,
        long followingCount,
        long looksCount,
        boolean isFollowing,
        boolean isMe
) {
}
