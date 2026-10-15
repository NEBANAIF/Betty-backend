package com.company.erp.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidTransferException extends ErpException {
    public InvalidTransferException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER", message);
    }
}
