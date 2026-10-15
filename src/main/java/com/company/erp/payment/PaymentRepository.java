package com.company.erp.payment;

import com.company.erp.loan.Loan;
import com.company.erp.sales.Sale;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Root;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    List<Payment> findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
            PaymentReferenceType referenceType, UUID referenceId);

    /**
     * The Payments page table. Optional filters are only applied when given (Specification), so no
     * null is ever bound to the query - see SearchSpecs for why that matters on PostgreSQL.
     */
    default Page<Payment> search(List<UUID> branchIds, PaymentMethod method, UUID bankId,
                                 PaymentReferenceType referenceType, Instant from, Instant to,
                                 Pageable pageable) {
        return search(branchIds, method, bankId, referenceType, from, to, null, pageable);
    }

    /**
     * Same again, plus a text search for the customer's name or phone or the sale number. A payment only points at
     * a sale or a loan by id, so the match goes through those (supplier payments have no customer and drop out
     * while a search is typed).
     */
    default Page<Payment> search(List<UUID> branchIds, PaymentMethod method, UUID bankId,
                                 PaymentReferenceType referenceType, Instant from, Instant to, String search,
                                 Pageable pageable) {
        Specification<Payment> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(SearchSpecs.idIn(root.get("branch").get("id"), branchIds, cb));
            if (method != null) {
                p.add(cb.equal(root.get("method"), method));
            }
            if (bankId != null) {
                p.add(cb.equal(root.get("bank").get("id"), bankId));
            }
            if (referenceType != null) {
                p.add(cb.equal(root.get("referenceType"), referenceType));
            }
            if (SearchSpecs.hasText(search)) {
                String term = search.trim();
                Subquery<UUID> sales = query.subquery(UUID.class);
                Root<Sale> sale = sales.from(Sale.class);
                Join<Object, Object> saleCustomer = sale.join("customer", JoinType.LEFT);
                sales.select(sale.<UUID>get("id")).where(cb.or(
                        SearchSpecs.containsIgnoreCase(sale.<String>get("saleNumber"), term, cb),
                        SearchSpecs.containsIgnoreCase(saleCustomer.<String>get("name"), term, cb),
                        SearchSpecs.containsIgnoreCase(saleCustomer.<String>get("phone"), term, cb)));

                Subquery<UUID> loans = query.subquery(UUID.class);
                Root<Loan> loan = loans.from(Loan.class);
                Join<Object, Object> loanSale = loan.join("sale", JoinType.LEFT);
                Join<Object, Object> loanCustomer = loan.join("customer", JoinType.LEFT);
                loans.select(loan.<UUID>get("id")).where(cb.or(
                        SearchSpecs.containsIgnoreCase(loanSale.<String>get("saleNumber"), term, cb),
                        SearchSpecs.containsIgnoreCase(loanCustomer.<String>get("name"), term, cb),
                        SearchSpecs.containsIgnoreCase(loanCustomer.<String>get("phone"), term, cb)));

                p.add(cb.or(
                        cb.and(cb.equal(root.get("referenceType"), PaymentReferenceType.SALE),
                                root.<UUID>get("referenceId").in(sales)),
                        cb.and(cb.equal(root.get("referenceType"), PaymentReferenceType.LOAN_PAYMENT),
                                root.<UUID>get("referenceId").in(loans))));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), to));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return findAll(spec, SearchSpecs.withDefaultSort(pageable, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
