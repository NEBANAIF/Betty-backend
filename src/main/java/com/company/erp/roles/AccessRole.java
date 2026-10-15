package com.company.erp.roles;

import com.company.erp.common.audit.BaseEntity;
import com.company.erp.user.Permission;
import com.company.erp.user.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * A named set of permissions that users are assigned to - what the Roles &
 * Permissions page edits. The five built-in roles are rows here too
 * (systemRole = true); the Super Admin can create more.
 * <p>
 * baseRole is the STRUCTURAL kind of the role (does it belong to a store or a
 * warehouse, is it a manager, or is it the all-seeing Super Admin). It decides
 * things like which branch type a user may be assigned to; it grants nothing.
 * What a role may actually do is only its permissions.
 * <p>
 * The permissions are loaded EAGERLY on purpose: the JWT filter builds the
 * caller's authorities on every request outside any transaction, and a lazy
 * collection there would fail. The set is small (a few dozen names).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "access_roles")
public class AccessRole extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_role", nullable = false, length = 30)
    private Role baseRole;

    /** One of the five built-in roles: cannot be deleted, and its name/kind never change. */
    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "access_role_permissions", joinColumns = @JoinColumn(name = "access_role_id"))
    @Column(name = "permission", nullable = false, length = 60)
    private Set<String> permissions = new HashSet<>();

    /**
     * The permissions as enum values. A stored name that no longer exists in
     * the Permission enum (a switch removed in a later version) is ignored
     * rather than breaking every login.
     */
    public Set<Permission> permissionSet() {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (String name : permissions) {
            try {
                result.add(Permission.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // permission no longer exists
            }
        }
        return result;
    }
}
