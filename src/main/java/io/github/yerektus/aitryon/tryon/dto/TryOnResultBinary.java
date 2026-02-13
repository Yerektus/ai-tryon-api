package io.github.yerektus.aitryon.tryon.dto;

public record TryOnResultBinary(
        byte[] bytes,
        String mimeType
) {
}
