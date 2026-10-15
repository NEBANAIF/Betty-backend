package com.company.erp.customer;

import com.company.erp.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Customer sales/loan history (spec section 14) lives against this
 * entity's id from Sale.customer and Loan.customer - both wired here in
 * Phase 6/7, kept minimal in this entity itself.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String phone;

    private String email;

    private String address;

    @Column(nullable = false)
    private boolean active = true;
}
