package com.company.erp.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductIdAndBranchId(UUID productId, UUID branchId);

    /**
     * Row-locked lookup used by StockMutationService for every
     * increase/decrease, so concurrent mutations of the same product+branch
     * inventory row serialize rather than lost-update-racing each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.branch.id = :branchId")
    Optional<Inventory> findForUpdate(@Param("productId") UUID productId, @Param("branchId") UUID branchId);

    List<Inventory> findByBranchId(UUID branchId);

    List<Inventory> findByBranchIdIn(List<UUID> branchIds);

    List<Inventory> findByProductId(UUID productId);

    /**
     * "Low stock" = has SOME stock but at or below the product's
     * reorderLevel. Out-of-stock rows are deliberately excluded here (they
     * are their own KPI) - see countOutOfStock/findOutOfStock.
     */
    @Query("""
            SELECT COUNT(i) FROM Inventory i
            WHERE i.branch.id IN :branchIds
              AND (i.quantity - i.reservedQuantity) > 0
              AND (i.quantity - i.reservedQuantity) <= i.product.reorderLevel
            """)
    long countLowStock(@Param("branchIds") List<UUID> branchIds);

    @Query("""
            SELECT COUNT(i) FROM Inventory i
            WHERE i.branch.id IN :branchIds AND (i.quantity - i.reservedQuantity) <= 0
            """)
    long countOutOfStock(@Param("branchIds") List<UUID> branchIds);

    /**
     * "In stock" = available above zero AND above the product's reorderLevel.
     * Together with countLowStock and countOutOfStock this partitions every
     * inventory row exactly once, which is what the dashboard's stock-status
     * donut relies on.
     */
    @Query("""
            SELECT COUNT(i) FROM Inventory i
            WHERE i.branch.id IN :branchIds
              AND (i.quantity - i.reservedQuantity) > 0
              AND (i.quantity - i.reservedQuantity) > i.product.reorderLevel
            """)
    long countInStock(@Param("branchIds") List<UUID> branchIds);

    /**
     * The dashboard's "Low stock alerts" widget - EVERY row at or below
     * reorder level, out-of-stock included, so the alert list doesn't miss
     * the most urgent case. Ordered most-critical (lowest available) first.
     */
    @Query("""
            SELECT i FROM Inventory i
            WHERE i.branch.id IN :branchIds
              AND (i.quantity - i.reservedQuantity) <= i.product.reorderLevel
            ORDER BY (i.quantity - i.reservedQuantity) ASC
            """)
    List<Inventory> findLowStockAlerts(@Param("branchIds") List<UUID> branchIds, Pageable pageable);

    /**
     * Available quantity (quantity - reserved) per product, added up over the given branches.
     * One row per product that has at least one stock row there: [productId, sum]. Products
     * with no row are simply absent - the caller treats them as 0. Feeds the in/low/out-of-stock
     * status on the Products page and the stock section of Reports.
     */
    @Query("""
            SELECT i.product.id, SUM(i.quantity - i.reservedQuantity) FROM Inventory i
            WHERE i.branch.id IN :branchIds
            GROUP BY i.product.id
            """)
    List<Object[]> sumAvailableByProduct(@Param("branchIds") List<UUID> branchIds);
}
