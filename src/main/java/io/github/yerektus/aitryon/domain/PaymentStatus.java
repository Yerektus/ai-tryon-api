package io.github.yerektus.aitryon.domain;

public enum PaymentStatus {
    CREATED,
    PENDING,
    PAID,
    FAILED,
    EXPIRED,
    CANCELED;

    public boolean isTerminal() {
        return this == PAID || this == FAILED || this == EXPIRED || this == CANCELED;
    }
}
