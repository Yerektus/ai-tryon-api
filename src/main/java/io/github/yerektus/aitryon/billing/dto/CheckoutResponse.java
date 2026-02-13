package io.github.yerektus.aitryon.billing.dto;

import java.time.Instant;
import java.util.UUID;

public record CheckoutResponse(
        UUID paymentId,
        String provider,
        String redirectUrl,
        Instant expiresAt
) {
}
