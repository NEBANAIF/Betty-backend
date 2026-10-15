package com.company.erp.returns.supplier;

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

public interface SupplierReturnRepository extends JpaRepository<SupplierReturn, UUID>, JpaSpecificationExecutor<SupplierReturn> {

    default Page<SupplierReturn> search(List<UUID> branchIds, UUID supplierId, UUID purchaseOrderId,
                                        Instant from, Instant to, String search, Pageable pageable) {
        Specification<SupplierReturn> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(SearchSpecs.idIn(root.get("branch").get("id"), branchIds, cb));
            if (supplierId != null) {
                p.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (purchaseOrderId != null) {
                p.add(cb.equal(root.get("purchaseOrder").get("id"), purchaseOrderId));
            }
            if (SearchSpecs.hasText(search)) {
                String term = search.trim();
                Join<Object, Object> supplier = root.join("supplier", JoinType.LEFT);
                Join<Object, Object> order = root.join("purchaseOrder", JoinType.LEFT);
                p.add(cb.or(
                        SearchSpecs.containsIgnoreCase(supplier.<String>get("name"), term, cb),
                        SearchSpecs.containsIgnoreCase(order.<String>get("orderNumber"), term, cb)));
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
