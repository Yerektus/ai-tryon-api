package io.github.yerektus.aitryon.social.dto;

import java.util.UUID;

public record FollowRelationResponse(
        UUID userId,
        boolean isFollowing,
        long followersCount
) {
}
