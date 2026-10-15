package com.company.erp.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ErpException {
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
