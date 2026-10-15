package com.company.erp.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends ErpException {
    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", message);
    }
}
