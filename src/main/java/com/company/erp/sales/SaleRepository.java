package com.company.erp.sales;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Join;
import com.company.erp.common.query.SearchSpecs;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID>, JpaSpecificationExecutor<Sale> {

    boolean existsBySaleNumber(String saleNumber);

    long countByBranchIdAndStatus(UUID branchId, SaleStatus status);

    /** Revenue for the branch P&L (spec section 24) - gross sales value, before any return refunds are netted out. */
    @Query("""
            SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s
            WHERE s.branch.id IN :branchIds AND s.status = :status
              AND s.createdAt >= :from AND s.createdAt <= :to
            """)
    BigDecimal sumRevenueForBranchesInRange(@Param("branchIds") List<UUID> branchIds,
                                             @Param("status") SaleStatus status,
                                             @Param("from") Instant from,
                                             @Param("to") Instant to);

    /**
     * Day-bucketed revenue + sale count for the dashboard/report sales
     * trend - native query (date_trunc is Postgres-specific, which this
     * app targets exclusively - see architecture doc). Each row is
     * [day (Timestamp), revenue (BigDecimal), salesCount (Long)].
     */
    @Query(value = """
            SELECT date_trunc('day', s.created_at) AS day,
                   COALESCE(SUM(s.total_amount), 0) AS revenue,
                   COUNT(*) AS sales_count
            FROM sales s
            WHERE s.branch_id IN (:branchIds) AND s.status = 'COMPLETED'
              AND s.created_at >= :from AND s.created_at <= :to
            GROUP BY day
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> dailyRevenueTrend(@Param("branchIds") List<UUID> branchIds,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to);

    /**
     * Optional filters are only applied when supplied (Specification), so no
     * null is ever bound to the query - see SearchSpecs.
     */
    default Page<Sale> search(List<UUID> branchIds, UUID customerId, PaymentStatus paymentStatus,
                              SaleStatus status, Instant from, Instant to, String search, Pageable pageable) {
        return search(branchIds, customerId, paymentStatus, status, from, to, search, null, pageable);
    }

    /** Same as above, plus an optional product filter: only sales that have a line for that product. */
    default Page<Sale> search(List<UUID> branchIds, UUID customerId, PaymentStatus paymentStatus,
                              SaleStatus status, Instant from, Instant to, String search, UUID productId,
                              Pageable pageable) {
        Specification<Sale> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(SearchSpecs.idIn(root.get("branch").get("id"), branchIds, cb));
            if (customerId != null) {
                p.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            if (paymentStatus != null) {
                p.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), to));
            }
            if (SearchSpecs.hasText(search)) {
                // One search box for everything people type: the sale number, the customer's name or phone,
                // or the name of a product sold on it. Walk-in sales have no customer, hence the LEFT join.
                String term = search.trim();
                Join<Object, Object> customer = root.join("customer", JoinType.LEFT);
                Subquery<UUID> soldProduct = query.subquery(UUID.class);
                Root<SaleItem> line = soldProduct.from(SaleItem.class);
                soldProduct.select(line.get("sale").<UUID>get("id"))
                        .where(SearchSpecs.containsIgnoreCase(line.get("product").<String>get("name"), term, cb));
                p.add(cb.or(
                        SearchSpecs.containsIgnoreCase(root.<String>get("saleNumber"), term, cb),
                        SearchSpecs.containsIgnoreCase(customer.<String>get("name"), term, cb),
                        SearchSpecs.containsIgnoreCase(customer.<String>get("phone"), term, cb),
                        root.<UUID>get("id").in(soldProduct)));
            }
            if (productId != null) {
                Subquery<UUID> withProduct = query.subquery(UUID.class);
                Root<SaleItem> item = withProduct.from(SaleItem.class);
                withProduct.select(item.get("sale").<UUID>get("id"))
                        .where(cb.equal(item.get("product").get("id"), productId));
                p.add(root.<UUID>get("id").in(withProduct));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return findAll(spec, SearchSpecs.withDefaultSort(pageable, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
