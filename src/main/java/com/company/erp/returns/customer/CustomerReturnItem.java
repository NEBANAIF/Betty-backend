package com.company.erp.returns.customer;

import com.company.erp.common.audit.BaseEntity;
import com.company.erp.sales.SaleBatchAllocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * References a SaleBatchAllocation directly - not a bare (saleItem, batch)
 * pair as the ERD's flat summary might suggest - because a single SaleItem
 * can be FIFO-fulfilled from multiple batches at different costs. Pinning
 * a return to the exact allocation line is what lets it:
 * - restock the EXACT batch the units came from (cost lineage preserved),
 *   never averaged or guessed;
 * - refund at that SaleItem's actual unitPrice (what the customer paid),
 *   never the allocation's unitCost (what the business paid the supplier)
 *   - see CustomerReturnService for that distinction;
 * - be validated against how much of THAT SPECIFIC line was already
 *   returned before, not just the SaleItem's total.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customer_return_items")
public class CustomerReturnItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_return_id", nullable = false)
    private CustomerReturn customerReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_batch_allocation_id", nullable = false)
    private SaleBatchAllocation saleBatchAllocation;

    @Column(name = "quantity_returned", nullable = false)
    private Integer quantityReturned;

    /** false = written off (damaged/defective): no stock movement, refund only. */
    @Column(nullable = false)
    private boolean restocked = true;

    @Column(name = "refund_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal refundAmount;
}
