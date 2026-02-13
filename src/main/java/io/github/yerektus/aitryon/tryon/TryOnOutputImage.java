package io.github.yerektus.aitryon.tryon;

public record TryOnOutputImage(
        TryOnOutputId id,
        byte[] bytes,
        String mimeType
) {
}
