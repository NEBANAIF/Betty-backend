package com.company.erp.branch;

import com.company.erp.common.audit.BaseEntity;
import com.company.erp.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A Branch is an operational unit - either a STORE or a WAREHOUSE. There is
 * intentionally no separate "Location" entity: a branch IS the location.
 *
 * Branches are never auto-created (no implicit "Main" branch); every branch
 * is explicitly created by a SUPER_ADMIN via the Branch Management screen
 * (Phase 3).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "branches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_branch_name", columnNames = "name"),
        @UniqueConstraint(name = "uk_branch_code", columnNames = "code")
})
public class Branch extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BranchType type;

    private String address;

    private String phone;

    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    @Column(nullable = false)
    private boolean active = true;
}
