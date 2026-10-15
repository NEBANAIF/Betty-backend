package com.company.erp.batch;

import java.util.ArrayList;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import jakarta.persistence.criteria.Predicate;
import com.company.erp.common.query.SearchSpecs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductBatchRepository extends JpaRepository<ProductBatch, UUID>, JpaSpecificationExecutor<ProductBatch> {

    boolean existsByBatchNumber(String batchNumber);

    /**
     * FIFO order: oldest receivedDate first, then oldest createdAt as a
     * tiebreaker for same-day receipts. Only batches with stock left are
     * returned. PESSIMISTIC_WRITE locks each returned row for the duration
     * of the transaction, so two concurrent sales against the same batch
     * serialize instead of racing past the remaining-quantity check - this
     * is the actual mechanism (paired with @Version as a belt-and-suspenders
     * check) that prevents negative stock under concurrency.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM ProductBatch b
            WHERE b.product.id = :productId
              AND b.branch.id = :branchId
              AND b.remainingQuantity > 0
            ORDER BY b.receivedDate ASC, b.createdAt ASC
            """)
    List<ProductBatch> findConsumableForUpdate(@Param("productId") UUID productId,
                                                @Param("branchId") UUID branchId);

    /**
     * Same FIFO-order, row-locked shape as findConsumableForUpdate above,
     * but scoped to the batch(es) created by one specific purchase-order
     * line - what Supplier Returns consumes from instead of the
     * product+branch-wide query, so a return can never draw stock (and
     * misattribute its cost) from an unrelated purchase or supplier.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM ProductBatch b
            WHERE b.sourcePurchaseOrderItemId = :purchaseOrderItemId
              AND b.remainingQuantity > 0
            ORDER BY b.receivedDate ASC, b.createdAt ASC
            """)
    List<ProductBatch> findConsumableForUpdateByPurchaseOrderItem(
            @Param("purchaseOrderItemId") UUID purchaseOrderItemId);

    /** Row-locked lookup of one batch - used when the POS sells from a specific, cashier-chosen batch. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ProductBatch b WHERE b.id = :id")
    Optional<ProductBatch> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Same FIFO-order, row-locked shape as findConsumableForUpdate(productId, branchId),
     * but across every branch in the caller's scope rather than one - used to find
     * "the oldest batch still in stock" for a product when correcting a cost that was
     * recorded as 0 (see BatchCostCorrectionService), where the caller may be a
     * SUPER_ADMIN whose scope spans several branches.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM ProductBatch b
            WHERE b.product.id = :productId
              AND b.branch.id IN :branchIds
              AND b.remainingQuantity > 0
            ORDER BY b.receivedDate ASC, b.createdAt ASC
            """)
    List<ProductBatch> findConsumableForUpdate(@Param("productId") UUID productId,
                                                @Param("branchIds") List<UUID> branchIds);

    /** Batches of one product at one branch that still have stock, oldest first (what the POS offers to sell from). */
    @Query("""
            SELECT b FROM ProductBatch b
            WHERE b.product.id = :productId AND b.branch.id = :branchId AND b.remainingQuantity > 0
            ORDER BY b.receivedDate ASC, b.createdAt ASC
            """)
    List<ProductBatch> findAvailable(@Param("productId") UUID productId, @Param("branchId") UUID branchId);

    List<ProductBatch> findBySourcePurchaseOrderItemIdIn(List<UUID> purchaseOrderItemIds);

    List<ProductBatch> findByProductIdAndBranchIdOrderByReceivedDateAsc(UUID productId, UUID branchId);

    List<ProductBatch> findByBranchIdOrderByReceivedDateAsc(UUID branchId);

    /**
     * The Batches list. Optional product and received-date range; filters are only added when supplied (see
     * SearchSpecs for why no null is ever bound).
     */
    default org.springframework.data.domain.Page<ProductBatch> search(
            List<UUID> branchIds, UUID productId, LocalDate from, LocalDate to,
            org.springframework.data.domain.Pageable pageable) {
        Specification<ProductBatch> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(SearchSpecs.idIn(root.get("branch").get("id"), branchIds, cb));
            if (productId != null) {
                p.add(cb.equal(root.get("product").get("id"), productId));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("receivedDate"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("receivedDate"), to));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return findAll(spec, SearchSpecs.withDefaultSort(pageable,
                Sort.by(Sort.Order.desc("receivedDate"), Sort.Order.desc("createdAt"))));
    }

    /**
     * Company-wide inventory valuation input: remaining_quantity * cost_price
     * summed per branch, computed in the database rather than pulled into
     * memory.
     */
    @Query("""
            SELECT COALESCE(SUM(b.remainingQuantity * b.costPrice), 0)
            FROM ProductBatch b
            WHERE b.branch.id = :branchId
            """)
    java.math.BigDecimal sumInventoryValueForBranch(@Param("branchId") UUID branchId);

    /** Same as above but summed across a resolved set of branches in one query, for dashboard/report scopes wider than one branch. */
    @Query("""
            SELECT COALESCE(SUM(b.remainingQuantity * b.costPrice), 0)
            FROM ProductBatch b
            WHERE b.branch.id IN :branchIds
            """)
    java.math.BigDecimal sumInventoryValueForBranches(@Param("branchIds") List<UUID> branchIds);

    /**
     * Every batch that still has stock across the given branches, oldest
     * first (FIFO order) - the raw input for per-product cost and stock
     * value figures (see ProductCostService).
     */
    @Query("""
            SELECT b FROM ProductBatch b
            WHERE b.branch.id IN :branchIds AND b.remainingQuantity > 0
            ORDER BY b.receivedDate ASC, b.createdAt ASC
            """)
    List<ProductBatch> findInStockForBranches(@Param("branchIds") List<UUID> branchIds);
}
