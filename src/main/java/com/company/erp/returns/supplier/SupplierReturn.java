package com.company.erp.returns.supplier;

import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.purchase.PurchaseOrder;
import com.company.erp.returns.ReturnStatus;
import com.company.erp.supplier.Supplier;
import com.company.erp.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Goods sent back to a supplier against one PurchaseOrder (spec section
 * 22): select supplier -> select purchase -> select items -> validate
 * stock -> remove stock -> update batch -> stock history -> adjust
 * supplier balance. supplier/branch are copied from the PurchaseOrder at
 * creation (never independently chosen) - a return can only ever be
 * against the order it's returning from.
 * <p>
 * "Adjust supplier balance" is satisfied without a stored, mutable balance
 * field: SupplierService.getBalance() nets returned value out of
 * purchased value on every read (SupplierReturnAllocationRepository
 * .sumReturnedValueForSupplier), the same derived-not-stored approach
 * already used for COGS, gross profit, and customer loan balances.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "supplier_returns")
public class SupplierReturn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnStatus status = ReturnStatus.COMPLETED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processed_by", nullable = false)
    private User processedBy;

    @OneToMany(mappedBy = "supplierReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SupplierReturnItem> items = new ArrayList<>();

    public void addItem(SupplierReturnItem item) {
        item.setSupplierReturn(this);
        items.add(item);
    }
}
