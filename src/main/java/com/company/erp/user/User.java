package com.company.erp.user;

import com.company.erp.branch.Branch;
import com.company.erp.roles.AccessRole;
import com.company.erp.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Application user.
 *
 * Business rule enforced at the service layer (UserService), not here:
 * every user whose role is not SUPER_ADMIN MUST have a non-null branch,
 * and that branch's type must match the role
 * (STORE_MANAGER/STORE_STAFF -> STORE, WAREHOUSE_MANAGER/WAREHOUSE_STAFF -> WAREHOUSE).
 * SUPER_ADMIN must have a null branch (unrestricted).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_users")
public class User extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * The STRUCTURAL kind of this user's role (always equal to accessRole.baseRole):
     * SUPER_ADMIN vs a store/warehouse manager or staff. Used for branch rules and
     * "is this the Super Admin" checks; it grants no permissions by itself.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    /**
     * What this user may actually do: a named permission set edited on the Roles page.
     * EAGER because the JWT filter reads it on every request outside a transaction.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "access_role_id")
    private AccessRole accessRole;

    /**
     * Null only for SUPER_ADMIN. Enforced in UserService, not via a DB
     * constraint, since the rule is conditional on role.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(nullable = false)
    private boolean active = true;
}
