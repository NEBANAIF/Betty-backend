package com.company.erp.sales;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {

    /**
     * Per-product quantity sold and revenue, for best-selling/slow-moving
     * product reports (spec sections 26/27) - each row is
     * [productId, productName, sku, totalQuantity, totalRevenue].
     * Returned as Object[] rather than a constructor-expression DTO to
     * avoid JPQL's constructor-expression numeric-type friction (SUM over
     * an Integer column vs a BigDecimal parameter); ReportService maps it.
     */
    @Query("""
            SELECT i.product.id, i.product.name, i.product.sku, SUM(i.quantity), SUM(i.lineTotal)
            FROM SaleItem i
            WHERE i.sale.branch.id IN :branchIds
              AND i.sale.status = :status
              AND i.sale.createdAt >= :from AND i.sale.createdAt <= :to
            GROUP BY i.product.id, i.product.name, i.product.sku
            ORDER BY SUM(i.quantity) DESC
            """)
    List<Object[]> sumQuantityAndRevenueByProduct(@Param("branchIds") List<UUID> branchIds,
                                                    @Param("status") SaleStatus status,
                                                    @Param("from") Instant from,
                                                    @Param("to") Instant to);
}
