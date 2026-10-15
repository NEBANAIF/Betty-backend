package com.company.erp.product;

import com.company.erp.common.query.SearchSpecs;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    boolean existsBySkuIgnoreCase(String sku);

    Optional<Product> findBySkuIgnoreCase(String sku);

    /** idFilter: when not null, only products with one of these ids (an empty set matches nothing). */
    default Page<Product> search(boolean activeOnly, String search, Set<UUID> idFilter, Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (activeOnly) {
                p.add(cb.isTrue(root.<Boolean>get("active")));
            }
            if (SearchSpecs.hasText(search)) {
                String term = search.trim();
                p.add(cb.or(
                        SearchSpecs.containsIgnoreCase(root.<String>get("name"), term, cb),
                        SearchSpecs.containsIgnoreCase(root.<String>get("sku"), term, cb)));
            }
            if (idFilter != null) {
                p.add(idFilter.isEmpty() ? cb.disjunction() : root.get("id").in(idFilter));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        // The old JPQL had no ORDER BY, which makes paging non-deterministic;
        // default to name order unless the caller asked for a specific sort.
        return findAll(spec, SearchSpecs.withDefaultSort(pageable, Sort.by(Sort.Direction.ASC, "name")));
    }
}
