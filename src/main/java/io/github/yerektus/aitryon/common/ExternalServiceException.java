package io.github.yerektus.aitryon.common;

import org.springframework.http.HttpStatus;

public class ExternalServiceException extends ApiException {
    public ExternalServiceException(String message) {
        super(HttpStatus.BAD_GATEWAY, "external_service_error", message);
    }
}
