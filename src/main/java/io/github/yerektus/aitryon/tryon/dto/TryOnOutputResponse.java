package io.github.yerektus.aitryon.tryon.dto;

public record TryOnOutputResponse(
        String id,
        String imageBase64,
        String mimeType
) {
}
