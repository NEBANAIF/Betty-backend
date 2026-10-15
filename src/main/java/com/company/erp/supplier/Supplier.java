package com.company.erp.supplier;

import com.company.erp.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Suppliers are global company data, like Product - a supplier
 * sells to the whole company, not to one branch, even though individual
 * PurchaseOrders against them are branch-scoped.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "suppliers")
public class Supplier extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String phone;

    private String email;

    private String address;

    @Column(name = "tax_number")
    private String taxNumber;

    @Column(nullable = false)
    private boolean active = true;
}
