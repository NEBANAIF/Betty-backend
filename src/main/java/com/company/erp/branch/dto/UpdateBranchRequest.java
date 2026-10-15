package com.company.erp.branch.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Branch type is intentionally NOT editable after creation: a STORE
 * flipping into a WAREHOUSE (or vice versa) would silently invalidate
 * every existing STORE_MANAGER/STORE_STAFF (or warehouse-role) user
 * assignment and every historical branch-type-scoped report. If the type
 * was chosen wrong, the correct fix is deactivating the branch and
 * creating a new one, not mutating it in place.
 */
public record UpdateBranchRequest(
        @NotBlank String name,
        String address,
        String phone,
        @Email String email
) {
}
