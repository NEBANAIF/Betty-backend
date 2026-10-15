package com.company.erp.returns.customer;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Join;
import java.util.ArrayList;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import jakarta.persistence.criteria.Predicate;
import com.company.erp.common.query.SearchSpecs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CustomerReturnRepository extends JpaRepository<CustomerReturn, UUID>, JpaSpecificationExecutor<CustomerReturn> {

    default Page<CustomerReturn> search(List<UUID> branchIds, UUID saleId, UUID customerId,
                                        Instant from, Instant to, String search, Pageable pageable) {
        Specification<CustomerReturn> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(SearchSpecs.idIn(root.get("branch").get("id"), branchIds, cb));
            if (saleId != null) {
                p.add(cb.equal(root.get("sale").get("id"), saleId));
            }
            if (customerId != null) {
                p.add(cb.equal(root.get("customer").get("id"), customerId));
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
