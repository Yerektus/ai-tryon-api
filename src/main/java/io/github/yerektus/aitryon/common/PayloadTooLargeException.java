package io.github.yerektus.aitryon.common;

import org.springframework.http.HttpStatus;

public class PayloadTooLargeException extends ApiException {
    public PayloadTooLargeException(String message) {
        super(HttpStatus.CONTENT_TOO_LARGE, "payload_too_large", message);
    }
}
