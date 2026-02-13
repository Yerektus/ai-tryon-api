package io.github.yerektus.aitryon.billing;

import io.github.yerektus.aitryon.domain.PaymentStatus;

public record StripeStatusResult(
        String sessionId,
        PaymentStatus status,
        String rawPayload
) {
}
