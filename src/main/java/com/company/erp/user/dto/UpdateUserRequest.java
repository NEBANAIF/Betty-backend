package com.company.erp.user.dto;

import com.company.erp.user.Role;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Excludes password (see ResetPasswordRequest - a deliberately separate,
 * explicit action per spec section 29, not folded into a general edit)
 * and email (changing a user's login identity is unusual enough to
 * deserve its own guarded flow later rather than a silent field here).
 */
public record UpdateUserRequest(
        @NotBlank String name,
        String phone,
        Role role,
        UUID branchId,
        UUID roleId
) {
}
