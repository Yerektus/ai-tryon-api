package io.github.yerektus.aitryon.tryon;

public record OpenAiTryOnResult(
        byte[] bytes,
        String mimeType
) {
}
