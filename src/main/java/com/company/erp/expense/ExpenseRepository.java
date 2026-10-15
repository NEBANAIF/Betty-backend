package com.company.erp.expense;

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

public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    default Page<Expense> search(List<UUID> branchIds, ExpenseCategory category,
                                 LocalDate from, LocalDate to, Pageable pageable) {
        Specification<Expense> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(SearchSpecs.idIn(root.get("branch").get("id"), branchIds, cb));
            if (category != null) {
                p.add(cb.equal(root.get("category"), category));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("expenseDate"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("expenseDate"), to));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return findAll(spec, SearchSpecs.withDefaultSort(pageable,
                Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt"))));
    }

    /** Used by FinanceReportService for the branch P&L's Expenses figure - see that class for why this is queried directly rather than via FinancialTransaction. */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
            WHERE e.branch.id IN :branchIds AND e.expenseDate >= :from AND e.expenseDate <= :to
            """)
    BigDecimal sumForBranchesInRange(@Param("branchIds") List<UUID> branchIds,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);
}
