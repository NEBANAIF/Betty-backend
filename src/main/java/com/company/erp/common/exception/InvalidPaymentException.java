package com.company.erp.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidPaymentException extends ErpException {
    public InvalidPaymentException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT", message);
    }
}
