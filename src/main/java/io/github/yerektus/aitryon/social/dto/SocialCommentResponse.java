package io.github.yerektus.aitryon.social.dto;

import java.time.Instant;
import java.util.UUID;

public record SocialCommentResponse(
        UUID id,
        UUID lookId,
        SocialUserResponse author,
        String body,
        UUID parentId,
        Instant createdAt,
        long repliesCount,
        boolean isLikedByMe,
        long likesCount,
        boolean canDelete
) {
}
