package com.company.erp.returns.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CustomerReturnItemRepository extends JpaRepository<CustomerReturnItem, UUID> {

    /**
     * How many units against this exact SaleBatchAllocation line have
     * already been returned across ALL prior return transactions (a
     * customer can return part of a purchase today and more later) - used
     * to cap a new return at what's actually still returnable on that line.
     */
    @Query("""
            SELECT COALESCE(SUM(i.quantityReturned), 0) FROM CustomerReturnItem i
            WHERE i.saleBatchAllocation.id = :allocationId
            """)
    int sumReturnedForAllocation(@Param("allocationId") UUID allocationId);

    /**
     * Revenue given back to customers in this branch/date range, for the
     * P&L's net-revenue figure - the FULL refund amount regardless of
     * whether the goods were restocked, since the customer got their money
     * back either way.
     */
    @Query("""
            SELECT COALESCE(SUM(i.refundAmount), 0) FROM CustomerReturnItem i
            WHERE i.customerReturn.branch.id IN :branchIds
              AND i.customerReturn.createdAt >= :from AND i.customerReturn.createdAt <= :to
            """)
    BigDecimal sumRefundedRevenueForBranchesInRange(@Param("branchIds") List<UUID> branchIds,
                                                      @Param("from") Instant from,
                                                      @Param("to") Instant to);

    /**
     * COGS reversed for this branch/date range - ONLY the restocked
     * portion: those units go back to sellable inventory and are no
     * longer "cost of goods sold" until resold again. A written-off
     * (restocked=false) return still refunds revenue but its original
     * cost stays recorded as incurred - the goods are genuinely gone, so
     * economically the outcome is the same as if the sale had simply had
     * zero margin, not a phantom asset that isn't actually in inventory.
     */
    @Query("""
            SELECT COALESCE(SUM(i.quantityReturned * i.saleBatchAllocation.unitCost), 0) FROM CustomerReturnItem i
            WHERE i.customerReturn.branch.id IN :branchIds
              AND i.restocked = true
              AND i.customerReturn.createdAt >= :from AND i.customerReturn.createdAt <= :to
            """)
    BigDecimal sumReversedCogsForBranchesInRange(@Param("branchIds") List<UUID> branchIds,
                                                   @Param("from") Instant from,
                                                   @Param("to") Instant to);
}
