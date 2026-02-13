package io.github.yerektus.aitryon.billing.dto;

public record PaymentPackageResponse(
        String code,
        String title,
        int credits,
        long amountMinor,
        String currency
) {
}
