package com.company.erp.returns.customer;

import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.customer.Customer;
import com.company.erp.returns.ReturnStatus;
import com.company.erp.sales.Sale;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A return of previously sold items against one Sale (spec section 21).
 * Created and fully processed in a single transaction - see
 * CustomerReturnService.create - never a staged workflow.
 * <p>
 * customer is copied from the sale at creation and may be null: a walk-in
 * (no-customer) sale can still be returned, since the return is validated
 * against the sale/allocation, not against customer identity.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customer_returns")
public class CustomerReturn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnStatus status = ReturnStatus.COMPLETED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processed_by", nullable = false)
    private User processedBy;

    /** SUM(items.refundAmount) - what the customer is owed back, at the price they actually paid. */
    @Column(name = "refund_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal refundAmount;

    @OneToMany(mappedBy = "customerReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CustomerReturnItem> items = new ArrayList<>();

    public void addItem(CustomerReturnItem item) {
        item.setCustomerReturn(this);
        items.add(item);
    }
}
