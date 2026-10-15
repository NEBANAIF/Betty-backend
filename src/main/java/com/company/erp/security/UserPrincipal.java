package com.company.erp.security;

import com.company.erp.roles.AccessRole;
import com.company.erp.user.Permission;
import com.company.erp.user.Role;
import com.company.erp.user.RolePermissionRegistry;
import com.company.erp.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authentication principal used throughout the app. Authorities are
 * "ROLE_X" (one per user, for coarse @PreAuthorize("hasRole(...)")  checks)
 * plus one authority per fine-grained Permission the role grants, so
 * @PreAuthorize("hasAuthority('SALES_CREATE')") works without any custom
 * PermissionEvaluator wiring. branchId is null only for SUPER_ADMIN.
 */
public class UserPrincipal implements UserDetails {

    private final UUID userId;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final String roleName;
    private final Set<Permission> permissions;
    private final UUID branchId;
    private final boolean active;

    public UserPrincipal(User user) {
        this.userId = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.roleName = resolveRoleName(user);
        this.permissions = resolvePermissions(user);
        this.branchId = user.getBranch() != null ? user.getBranch().getId() : null;
        this.active = user.isActive();
    }

    /**
     * The Super Admin always has every permission (so nobody can be locked out by
     * editing a role). Everyone else gets exactly what their stored access role
     * grants. A user with no stored role (only possible in a bare object, never a
     * persisted user) falls back to the built-in defaults.
     */
    private static Set<Permission> resolvePermissions(User user) {
        if (user.getRole() == Role.SUPER_ADMIN) {
            return EnumSet.allOf(Permission.class);
        }
        AccessRole accessRole = user.getAccessRole();
        if (accessRole == null) {
            return RolePermissionRegistry.permissionsFor(user.getRole());
        }
        return accessRole.permissionSet();
    }

    private static String resolveRoleName(User user) {
        if (user.getAccessRole() != null && user.getAccessRole().getName() != null) {
            return user.getAccessRole().getName();
        }
        return user.getRole() != null ? user.getRole().name().replace('_', ' ') : "";
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public Role getRole() {
        return role;
    }

    /** Display name of the assigned role (e.g. "Cashier"), as opposed to its structural kind. */
    public String getRoleName() {
        return roleName;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public boolean isSuperAdmin() {
        return role == Role.SUPER_ADMIN;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of("ROLE_" + role.name()),
                getPermissions().stream().map(Enum::name)
        ).map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
