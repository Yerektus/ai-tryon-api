package io.github.yerektus.aitryon.common;

import org.springframework.http.HttpStatus;

public class PaymentRequiredException extends ApiException {
    public PaymentRequiredException(String message) {
        super(HttpStatus.PAYMENT_REQUIRED, "payment_required", message);
    }
}
