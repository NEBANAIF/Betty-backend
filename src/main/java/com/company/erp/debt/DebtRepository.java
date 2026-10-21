package com.company.erp.debt;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface DebtRepository extends JpaRepository<Debt, UUID>, JpaSpecificationExecutor<Debt> {

    java.util.Optional<Debt> findByPurchaseOrderId(UUID purchaseOrderId);

    /**
     * outstandingOnly hides fully paid debts. overdueOnly keeps unpaid debts whose due date has passed
     * (OVERDUE is never stored - see DebtStatus). search matches the lender name or description.
     */
    default Page<Debt> search(DebtType type, DebtStatus status, boolean outstandingOnly, boolean overdueOnly,
                              String search, Pageable pageable) {
        Specification<Debt> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (type != null) {
                p.add(cb.equal(root.get("debtType"), type));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (outstandingOnly) {
                p.add(root.get("status").in(DebtStatus.OPEN, DebtStatus.PARTIALLY_PAID));
            }
            if (overdueOnly) {
                p.add(root.get("status").in(DebtStatus.OPEN, DebtStatus.PARTIALLY_PAID));
                p.add(cb.isNotNull(root.get("dueDate")));
                p.add(cb.lessThan(root.get("dueDate"), LocalDate.now()));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("lenderName")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return findAll(spec, pageable);
    }
}
