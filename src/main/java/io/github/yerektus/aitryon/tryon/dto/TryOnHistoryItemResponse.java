package io.github.yerektus.aitryon.tryon.dto;

import java.time.Instant;
import java.util.UUID;

public record TryOnHistoryItemResponse(
        UUID jobId,
        String status,
        String clothingName,
        String clothingSize,
        int creditsSpent,
        Instant createdAt,
        String errorMessage,
        boolean hasResult
) {
}
