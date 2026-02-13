package io.github.yerektus.aitryon.tryon.dto;

import java.util.List;

public record TryOnStyleHintsResponse(
        List<TryOnStyleHintResponse> hints
) {
}
