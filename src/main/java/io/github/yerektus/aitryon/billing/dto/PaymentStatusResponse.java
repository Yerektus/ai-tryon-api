package io.github.yerektus.aitryon.billing.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentStatusResponse(
        UUID paymentId,
        String provider,
        String providerInvoiceId,
        String status,
        long amountMinor,
        String currency,
        int credits,
        String redirectUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
