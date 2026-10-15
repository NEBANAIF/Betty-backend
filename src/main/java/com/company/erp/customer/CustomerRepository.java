package com.company.erp.customer;

import com.company.erp.common.query.SearchSpecs;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    List<Customer> findByActiveTrue();

    /**
     * Built as a Specification so an absent search term adds no predicate at
     * all (see SearchSpecs for why the old "(:search IS NULL OR ...)" JPQL
     * failed on PostgreSQL).
     */
    default List<Customer> search(String search, boolean activeOnly) {
        Specification<Customer> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (SearchSpecs.hasText(search)) {
                String term = search.trim();
                p.add(cb.or(
                        SearchSpecs.containsIgnoreCase(root.<String>get("name"), term, cb),
                        SearchSpecs.contains(root.<String>get("phone"), term, cb)));
            }
            if (activeOnly) {
                p.add(cb.isTrue(root.<Boolean>get("active")));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return findAll(spec, Sort.by(Sort.Direction.ASC, "name"));
    }
}
