package com.company.erp.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Catch-all for business-rule violations that don't warrant their own
 * dedicated exception type (e.g. "cannot deactivate the last SUPER_ADMIN",
 * "STORE_MANAGER cannot be assigned to a WAREHOUSE branch").
 */
public class BusinessRuleViolationException extends ErpException {
    public BusinessRuleViolationException(String message) {
        super(HttpStatus.BAD_REQUEST, "BUSINESS_RULE_VIOLATION", message);
    }
}
