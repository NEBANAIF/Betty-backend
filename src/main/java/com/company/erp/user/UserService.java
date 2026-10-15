package com.company.erp.user;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.branch.BranchType;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.DuplicateResourceException;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.roles.AccessRole;
import com.company.erp.roles.AccessRoleRepository;
import com.company.erp.security.UserPrincipal;
import com.company.erp.user.dto.CreateUserRequest;
import com.company.erp.user.dto.ResetPasswordRequest;
import com.company.erp.user.dto.UpdateUserRequest;
import com.company.erp.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessRoleRepository accessRoleRepository;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("A user with email " + request.email() + " already exists");
        }

        AccessRole accessRole = resolveAccessRole(request.role(), request.roleId());
        assertMayManageSuperAdmin(accessRole.getBaseRole() == Role.SUPER_ADMIN);
        Branch branch = resolveAndValidateBranch(accessRole.getBaseRole(), request.branchId());

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(accessRole.getBaseRole());
        user.setAccessRole(accessRole);
        user.setBranch(branch);
        user.setActive(true);

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(UUID branchId, Role role, String search, Boolean active,
                                              java.time.Instant from, java.time.Instant to, Pageable pageable) {
        return PageResponse.of(userRepository.search(branchId, role, search, active, from, to, pageable)
                .map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.from(findOrThrow(id));
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = findOrThrow(id);
        AccessRole accessRole = resolveAccessRole(request.role(), request.roleId());
        assertMayManageSuperAdmin(user.getRole() == Role.SUPER_ADMIN || accessRole.getBaseRole() == Role.SUPER_ADMIN);
        if (user.getRole() == Role.SUPER_ADMIN && accessRole.getBaseRole() != Role.SUPER_ADMIN
                && user.isActive() && userRepository.countByRoleAndActiveTrue(Role.SUPER_ADMIN) <= 1) {
            throw new BusinessRuleViolationException("Cannot change the role of the last active SUPER_ADMIN");
        }
        Branch branch = resolveAndValidateBranch(accessRole.getBaseRole(), request.branchId());

        user.setName(request.name());
        user.setPhone(request.phone());
        user.setRole(accessRole.getBaseRole());
        user.setAccessRole(accessRole);
        user.setBranch(branch);

        return UserResponse.from(user);
    }

    /** Guards against ever locking every SUPER_ADMIN out of the system at once. */
    @Transactional
    public UserResponse setActive(UUID id, boolean active) {
        User user = findOrThrow(id);
        if (!active && user.getRole() == Role.SUPER_ADMIN
                && userRepository.countByRoleAndActiveTrue(Role.SUPER_ADMIN) <= 1) {
            throw new BusinessRuleViolationException("Cannot deactivate the last active SUPER_ADMIN");
        }
        user.setActive(active);
        return UserResponse.from(user);
    }

    @Transactional
    public void resetPassword(UUID id, ResetPasswordRequest request) {
        User user = findOrThrow(id);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    /**
     * The role a request names: by id (any role, built-in or custom) or, for callers that
     * only know a built-in kind (the seed runners), by that kind's built-in role.
     */
    private AccessRole resolveAccessRole(Role role, UUID roleId) {
        if (roleId != null) {
            return accessRoleRepository.findById(roleId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Role", roleId));
        }
        if (role != null) {
            return accessRoleRepository.findBySystemRoleTrueAndBaseRole(role)
                    .orElseThrow(() -> new BusinessRuleViolationException("The built-in role " + role + " is missing"));
        }
        throw new BusinessRuleViolationException("A role is required");
    }

    /**
     * Someone holding USER_MANAGE through a custom role must not be able to create, edit or
     * promote Super Admins. Nothing is checked when there is no logged-in caller (the startup
     * bootstrap that creates the first admin).
     */
    private void assertMayManageSuperAdmin(boolean involvesSuperAdmin) {
        if (!involvesSuperAdmin) {
            return;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal caller
                && !caller.isSuperAdmin()) {
            throw new ForbiddenException("Only a Super Admin can create or change Super Admin users");
        }
    }

    /**
     * Enforces, in one place:
     * - SUPER_ADMIN must NOT have a branch (unrestricted access).
     * - Every other role MUST have a branch.
     * - STORE_MANAGER / STORE_STAFF may only be assigned to a STORE branch.
     * - WAREHOUSE_MANAGER / WAREHOUSE_STAFF may only be assigned to a WAREHOUSE branch.
     */
    private Branch resolveAndValidateBranch(Role role, UUID branchId) {
        if (role == Role.SUPER_ADMIN) {
            if (branchId != null) {
                throw new BusinessRuleViolationException("SUPER_ADMIN users must not be assigned to a branch");
            }
            return null;
        }

        if (branchId == null) {
            throw new BusinessRuleViolationException("Role " + role + " requires an assigned branch");
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> ResourceNotFoundException.of("Branch", branchId));

        BranchType requiredType = switch (role) {
            case STORE_MANAGER, STORE_STAFF -> BranchType.STORE;
            case WAREHOUSE_MANAGER, WAREHOUSE_STAFF -> BranchType.WAREHOUSE;
            case SUPER_ADMIN -> null; // unreachable, handled above
        };

        if (branch.getType() != requiredType) {
            throw new BusinessRuleViolationException(
                    "Role " + role + " requires a " + requiredType + " branch, but " +
                            branch.getName() + " is a " + branch.getType() + " branch");
        }

        return branch;
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }
}
