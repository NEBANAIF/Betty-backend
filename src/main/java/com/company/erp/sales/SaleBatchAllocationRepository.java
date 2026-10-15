package com.company.erp.sales;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SaleBatchAllocationRepository extends JpaRepository<SaleBatchAllocation, UUID> {

    /**
     * COGS = SUM(quantityAllocated * unitCost) over every allocation
     * belonging to a COMPLETED sale in the given branches/date range -
     * spec section 24: "Do not calculate COGS using selling price." Used
     * by FinanceReportService; never by anything computing a single sale's
     * own COGS (that's SaleItemResponse.cogs(), summed the same way but
     * scoped to one sale).
     */
    @Query("""
            SELECT COALESCE(SUM(a.quantityAllocated * a.unitCost), 0)
            FROM SaleBatchAllocation a
            WHERE a.saleItem.sale.branch.id IN :branchIds
              AND a.saleItem.sale.status = :status
              AND a.saleItem.sale.createdAt >= :from AND a.saleItem.sale.createdAt <= :to
            """)
    BigDecimal sumCogsForBranchesInRange(@Param("branchIds") List<UUID> branchIds,
                                          @Param("status") SaleStatus status,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);

    /** Day-bucketed COGS, paired with SaleRepository.dailyRevenueTrend for the profit trend widget. Each row is [day, cogs]. */
    @Query(value = """
            SELECT date_trunc('day', s.created_at) AS day,
                   COALESCE(SUM(a.quantity_allocated * a.unit_cost), 0) AS cogs
            FROM sale_batch_allocations a
            JOIN sale_items si ON si.id = a.sale_item_id
            JOIN sales s ON s.id = si.sale_id
            WHERE s.branch_id IN (:branchIds) AND s.status = 'COMPLETED'
              AND s.created_at >= :from AND s.created_at <= :to
            GROUP BY day
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> dailyCogsTrend(@Param("branchIds") List<UUID> branchIds,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);
}
