package com.company.erp.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidReturnException extends ErpException {
    public InvalidReturnException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_RETURN", message);
    }
}
