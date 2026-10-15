package com.company.erp.common.dto;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape returned by every failed API call, matching the
 * example in the project spec:
 * { "timestamp", "status", "error", "message", "path" }
 * `fieldErrors` is populated only for bean-validation failures.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError withFieldErrors(int status, String error, String message, String path,
                                            List<FieldError> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, fieldErrors);
    }
}
