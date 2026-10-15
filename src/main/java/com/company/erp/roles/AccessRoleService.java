package com.company.erp.roles;

import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.DuplicateResourceException;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.roles.dto.AccessRoleRequest;
import com.company.erp.roles.dto.AccessRoleResponse;
import com.company.erp.roles.dto.PermissionInfo;
import com.company.erp.security.BranchAccessService;
import com.company.erp.user.Permission;
import com.company.erp.user.Role;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Creating and editing roles. Only a Super Admin may do any of it - being allowed
 * to open the Users page (USER_MANAGE) is not enough to change what other roles
 * can do, otherwise a role could simply grant itself everything.
 */
@Service
@RequiredArgsConstructor
public class AccessRoleService {

    private final AccessRoleRepository accessRoleRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;

    @Transactional(readOnly = true)
    public List<AccessRoleResponse> list() {
        return accessRoleRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((AccessRole r) -> !r.isSystemRole())
                        .thenComparing(r -> r.isSystemRole() ? r.getBaseRole().ordinal() : 0)
                        .thenComparing(r -> r.getName().toLowerCase()))
                .map(r -> AccessRoleResponse.from(r, userRepository.countByAccessRoleId(r.getId())))
                .toList();
    }

    /** Every switch that exists, with the words the editor shows next to it. */
    public List<PermissionInfo> catalog() {
        return Arrays.stream(Permission.values())
                .map(p -> new PermissionInfo(p.name(), p.getGroup(), p.getLabel(), p.getDescription()))
                .toList();
    }

    @Transactional
    public AccessRoleResponse create(AccessRoleRequest request) {
        requireSuperAdmin();

        String name = request.name().trim();
        if (accessRoleRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A role named \"" + name + "\" already exists");
        }

        AccessRole role = new AccessRole();
        role.setName(name);
        role.setDescription(blankToNull(request.description()));
        role.setBaseRole(baseRoleFor(request.branchType(), Boolean.TRUE.equals(request.canManageBranch())));
        role.setSystemRole(false);
        role.setPermissions(validatedPermissions(request.permissions()));

        return AccessRoleResponse.from(accessRoleRepository.save(role), 0);
    }

    @Transactional
    public AccessRoleResponse update(UUID id, AccessRoleRequest request) {
        requireSuperAdmin();

        AccessRole role = findOrThrow(id);
        if (role.getBaseRole() == Role.SUPER_ADMIN) {
            throw new BusinessRuleViolationException("The Super Admin role always has full access and cannot be edited");
        }

        // Built-in roles keep their name; only the permissions of a built-in role can change.
        if (!role.isSystemRole()) {
            String name = request.name().trim();
            if (!name.equalsIgnoreCase(role.getName()) && accessRoleRepository.existsByNameIgnoreCase(name)) {
                throw new DuplicateResourceException("A role named \"" + name + "\" already exists");
            }
            role.setName(name);
            role.setDescription(blankToNull(request.description()));
        }
        role.setPermissions(validatedPermissions(request.permissions()));

        return AccessRoleResponse.from(role, userRepository.countByAccessRoleId(id));
    }

    @Transactional
    public void delete(UUID id) {
        requireSuperAdmin();

        AccessRole role = findOrThrow(id);
        if (role.isSystemRole()) {
            throw new BusinessRuleViolationException("The built-in roles cannot be deleted");
        }
        long users = userRepository.countByAccessRoleId(id);
        if (users > 0) {
            throw new BusinessRuleViolationException("This role is assigned to " + users
                    + " user(s). Move them to another role before deleting it.");
        }
        accessRoleRepository.delete(role);
    }

    /** A role's structural kind, from the two choices offered when it is created. */
    static Role baseRoleFor(String branchType, boolean canManageBranch) {
        if (branchType == null || branchType.isBlank()) {
            throw new BusinessRuleViolationException("Choose whether this role works at a store or a warehouse");
        }
        return switch (branchType.trim().toUpperCase()) {
            case "STORE" -> canManageBranch ? Role.STORE_MANAGER : Role.STORE_STAFF;
            case "WAREHOUSE" -> canManageBranch ? Role.WAREHOUSE_MANAGER : Role.WAREHOUSE_STAFF;
            default -> throw new BusinessRuleViolationException("Unknown branch type: " + branchType);
        };
    }

    static Set<String> validatedPermissions(Set<String> requested) {
        Set<String> result = new HashSet<>();
        if (requested == null) {
            return result;
        }
        for (String name : requested) {
            try {
                result.add(Permission.valueOf(name).name());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new BusinessRuleViolationException("Unknown permission: " + name);
            }
        }
        return result;
    }

    private void requireSuperAdmin() {
        if (!branchAccessService.currentUser().isSuperAdmin()) {
            throw new ForbiddenException("Only a Super Admin can create or change roles");
        }
    }

    private AccessRole findOrThrow(UUID id) {
        return accessRoleRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Role", id));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
