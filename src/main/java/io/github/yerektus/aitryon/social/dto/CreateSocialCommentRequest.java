package io.github.yerektus.aitryon.social.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSocialCommentRequest(
        @NotBlank(message = "body is required")
        @Size(max = 400, message = "comment body too long")
        String body,
        UUID parentId
) {
}
