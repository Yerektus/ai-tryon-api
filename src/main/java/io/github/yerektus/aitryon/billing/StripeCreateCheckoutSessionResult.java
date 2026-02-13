package io.github.yerektus.aitryon.billing;

import io.github.yerektus.aitryon.domain.PaymentStatus;

import java.time.Instant;

public record StripeCreateCheckoutSessionResult(
        String sessionId,
        String redirectUrl,
        PaymentStatus status,
        Instant expiresAt,
        String rawPayload
) {
}
