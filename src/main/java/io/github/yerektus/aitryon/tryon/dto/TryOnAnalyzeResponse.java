package io.github.yerektus.aitryon.tryon.dto;

import java.util.UUID;
import java.util.List;

public record TryOnAnalyzeResponse(
        UUID jobId,
        String resultImageBase64,
        String resultMimeType,
        int creditsSpent,
        int remainingCredits,
        List<TryOnOutputResponse> outputs
) {
}
