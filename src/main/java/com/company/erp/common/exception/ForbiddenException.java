package com.company.erp.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown whenever a request touches a branch, or a resource scoped to a
 * branch, that the authenticated user is not permitted to access. This is
 * the exception the branch-isolation acceptance test expects to surface as
 * HTTP 403.
 */
public class ForbiddenException extends ErpException {
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ForbiddenException branchAccessDenied(Object branchId) {
        return new ForbiddenException("Access to branch " + branchId + " is not permitted for this user");
    }
}
