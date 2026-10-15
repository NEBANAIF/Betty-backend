package com.company.erp.roles.dto;

import com.company.erp.roles.AccessRole;
import com.company.erp.user.Permission;
import com.company.erp.user.Role;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

public record AccessRoleResponse(
        UUID id,
        String name,
        String description,
        String baseRole,
        String branchType,
        boolean canManageBranch,
        boolean systemRole,
        boolean superAdmin,
        long userCount,
        Set<String> permissions
) {
    public static AccessRoleResponse from(AccessRole role, long userCount) {
        Role base = role.getBaseRole();
        boolean superAdmin = base == Role.SUPER_ADMIN;
        String branchType = switch (base) {
            case SUPER_ADMIN -> null;
            case STORE_MANAGER, STORE_STAFF -> "STORE";
            case WAREHOUSE_MANAGER, WAREHOUSE_STAFF -> "WAREHOUSE";
        };
        boolean manager = base == Role.STORE_MANAGER || base == Role.WAREHOUSE_MANAGER;
        // The Super Admin always has every switch, whatever is stored.
        Set<String> permissions = superAdmin
                ? Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toCollection(TreeSet::new))
                : new TreeSet<>(role.getPermissions());
        return new AccessRoleResponse(role.getId(), role.getName(), role.getDescription(), base.name(),
                branchType, manager, role.isSystemRole(), superAdmin, userCount, permissions);
    }
}
