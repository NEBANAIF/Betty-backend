package com.company.erp.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ErpException {
    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }

    public static ResourceNotFoundException of(String entityName, Object id) {
        return new ResourceNotFoundException(entityName + " not found: " + id);
    }
}
