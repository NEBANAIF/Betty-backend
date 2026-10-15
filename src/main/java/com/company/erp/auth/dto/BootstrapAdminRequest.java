package com.company.erp.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for POST /api/auth/bootstrap-admin. Deliberately just name/email/
 * password - the caller (you, from Postman) supplies the first Super
 * Admin's credentials at request time so nothing ends up hardcoded in
 * source control. The endpoint refuses once any user already exists.
 */
public record BootstrapAdminRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password
) {
}
