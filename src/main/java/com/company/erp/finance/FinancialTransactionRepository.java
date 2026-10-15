package com.company.erp.finance;

import com.company.erp.common.query.SearchSpecs;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface FinancialTransactionRepository
        extends JpaRepository<FinancialTransaction, UUID>, JpaSpecificationExecutor<FinancialTransaction> {

    /** The audit-trail listing (drill-down report) - NOT used to compute the P&L, see FinancialTransaction's javadoc. */
    default Page<FinancialTransaction> search(List<UUID> branchIds, FinancialTransactionType type,
                                              LocalDate from, LocalDate to, Pageable pageable) {
        Specification<FinancialTransaction> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(SearchSpecs.idIn(root.get("branch").get("id"), branchIds, cb));
            if (type != null) {
                p.add(cb.equal(root.get("type"), type));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("occurredOn"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("occurredOn"), to));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return findAll(spec, SearchSpecs.withDefaultSort(pageable,
                Sort.by(Sort.Order.desc("occurredOn"), Sort.Order.desc("createdAt"))));
    }
}
