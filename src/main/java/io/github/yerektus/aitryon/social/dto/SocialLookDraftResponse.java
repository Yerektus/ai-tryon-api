package io.github.yerektus.aitryon.social.dto;

import java.time.Instant;
import java.util.List;

public record SocialLookDraftResponse(
        String title,
        String description,
        List<String> tags,
        String style,
        String visibility,
        String imageUrl,
        String imageDataUri,
        Instant updatedAt
) {
}
