package com.company.erp.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends ErpException {
    public InsufficientStockException(String message) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", message);
    }
}
