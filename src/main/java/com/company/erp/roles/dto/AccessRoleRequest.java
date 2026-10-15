package com.company.erp.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * branchType ("STORE" or "WAREHOUSE") and canManageBranch are only used when a
 * role is CREATED; they fix the role's structural kind and are not editable
 * afterwards (changing them could leave existing users on a branch type that no
 * longer fits their role).
 */
public record AccessRoleRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 255) String description,
        String branchType,
        Boolean canManageBranch,
        Set<String> permissions
) {
}
