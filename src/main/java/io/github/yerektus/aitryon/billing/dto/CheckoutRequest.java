package io.github.yerektus.aitryon.billing.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(
        @NotBlank(message = "packageCode is required")
        String packageCode,

        @NotBlank(message = "successUrl is required")
        String successUrl,

        @NotBlank(message = "cancelUrl is required")
        String cancelUrl,

        @NotBlank(message = "platform is required")
        String platform
) {
}
