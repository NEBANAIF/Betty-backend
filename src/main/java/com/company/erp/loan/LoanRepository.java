package com.company.erp.loan;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Join;
import com.company.erp.common.query.SearchSpecs;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<Loan, UUID>, JpaSpecificationExecutor<Loan> {

    boolean existsBySaleId(UUID saleId);

    java.util.Optional<Loan> findBySaleId(UUID saleId);

    /**
     * status filters the persisted lifecycle only (OPEN/PARTIALLY_PAID/PAID
     * - see LoanStatus). overdueOnly is a separate flag rather than a status
     * value, since "overdue" is never a stored status - it's
     * remainingAmount > 0 AND dueDate in the past, evaluated fresh here.
     */
    default Page<Loan> search(List<UUID> branchIds, UUID customerId, LoanStatus status,
                              boolean overdueOnly, Pageable pageable) {
        return search(branchIds, customerId, status, overdueOnly, null, null, null, pageable);
    }

    /** Same again, plus an optional range on the due date and a text search (customer name/phone or sale number). */
    default Page<Loan> search(List<UUID> branchIds, UUID customerId, LoanStatus status,
                              boolean overdueOnly, LocalDate dueFrom, LocalDate dueTo, String search,
                              Pageable pageable) {
        Specification<Loan> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(SearchSpecs.idIn(root.get("branch").get("id"), branchIds, cb));
            if (customerId != null) {
                p.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (overdueOnly) {
                p.add(cb.greaterThan(root.<BigDecimal>get("remainingAmount"), BigDecimal.ZERO));
                p.add(cb.lessThan(root.<LocalDate>get("dueDate"), LocalDate.now()));
            }
            if (SearchSpecs.hasText(search)) {
                String term = search.trim();
                Join<Object, Object> customer = root.join("customer", JoinType.LEFT);
                Join<Object, Object> sale = root.join("sale", JoinType.LEFT);
                p.add(cb.or(
                        SearchSpecs.containsIgnoreCase(customer.<String>get("name"), term, cb),
                        SearchSpecs.containsIgnoreCase(customer.<String>get("phone"), term, cb),
                        SearchSpecs.containsIgnoreCase(sale.<String>get("saleNumber"), term, cb)));
            }
            if (dueFrom != null) {
                p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("dueDate"), dueFrom));
            }
            if (dueTo != null) {
                p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("dueDate"), dueTo));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return findAll(spec, SearchSpecs.withDefaultSort(pageable, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Query("SELECT COALESCE(SUM(l.originalAmount), 0) FROM Loan l WHERE l.customer.id = :customerId")
    BigDecimal sumOriginalForCustomer(@Param("customerId") UUID customerId);

    @Query("SELECT COALESCE(SUM(l.remainingAmount), 0) FROM Loan l WHERE l.customer.id = :customerId")
    BigDecimal sumOutstandingForCustomer(@Param("customerId") UUID customerId);
}
