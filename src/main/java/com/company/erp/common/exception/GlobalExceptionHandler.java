package com.company.erp.common.exception;

import com.company.erp.common.dto.ApiError;
import com.company.erp.common.i18n.MessageTranslator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Centralized exception -> HTTP response translation. No controller in this
 * codebase should ever catch a domain exception itself; they let it
 * propagate here so every endpoint returns the exact same error shape.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageTranslator translator;

    /** The message in the language the caller asked for (Accept-Language: am -> Amharic). */
    private String t(String message, HttpServletRequest request) {
        return translator.translate(message, request.getHeader("Accept-Language"));
    }

    @ExceptionHandler(ErpException.class)
    public ResponseEntity<ApiError> handleErpException(ErpException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(ex.getStatus().value(), ex.getErrorCode(), t(ex.getMessage(), request), request.getRequestURI());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), t(fe.getDefaultMessage(), request)))
                .toList();
        ApiError body = ApiError.withFieldErrors(
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
                t("One or more fields are invalid", request), request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(HttpStatus.FORBIDDEN.value(), "FORBIDDEN",
                t("You do not have permission to perform this action", request), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(HttpStatus.UNAUTHORIZED.value(), "INVALID_CREDENTIALS",
                t("Invalid email or password", request), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Log the full stack trace server-side. The client only ever gets the
        // generic message below, so without this line the real cause of a 500
        // is invisible to whoever is running the backend.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        ApiError body = ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                t("An unexpected error occurred", request), request.getRequestURI());
        return ResponseEntity.internalServerError().body(body);
    }
}
