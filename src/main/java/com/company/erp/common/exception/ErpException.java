package com.company.erp.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all domain exceptions. Every subclass fixes an HttpStatus
 * and a stable error code so the GlobalExceptionHandler can produce a
 * consistent { timestamp, status, error, message, path } body without any
 * per-exception branching logic.
 */
public abstract class ErpException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ErpException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
