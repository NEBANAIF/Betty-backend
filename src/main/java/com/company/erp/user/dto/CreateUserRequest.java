package com.company.erp.user.dto;

import com.company.erp.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * The role is given as roleId (any role from the Roles page). role - a built-in kind - is only for
 * internal callers such as the startup seeders; one of the two must be present.
 */
public record CreateUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String phone,
        @NotBlank @Size(min = 8) String password,
        Role role,
        UUID branchId,
        UUID roleId
) {
}
