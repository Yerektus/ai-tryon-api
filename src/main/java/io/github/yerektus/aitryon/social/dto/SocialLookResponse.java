package io.github.yerektus.aitryon.social.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SocialLookResponse(
        UUID id,
        SocialUserResponse author,
        String imageUrl,
        String title,
        String description,
        List<String> tags,
        String style,
        String visibility,
        long likesCount,
        long commentsCount,
        boolean isLikedByMe,
        Instant createdAt
) {
}
