package com.company.erp.stockhistory;

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

public interface StockHistoryRepository extends JpaRepository<StockHistory, UUID>, JpaSpecificationExecutor<StockHistory> {

    default Page<StockHistory> search(List<UUID> branchIds, UUID productId, StockMovementType movementType,
                                      Instant from, Instant to, Pageable pageable) {
        Specification<StockHistory> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(SearchSpecs.idIn(root.get("branch").get("id"), branchIds, cb));
            if (productId != null) {
                p.add(cb.equal(root.get("product").get("id"), productId));
            }
            if (movementType != null) {
                p.add(cb.equal(root.get("movementType"), movementType));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.<Instant>get("occurredAt"), to));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return findAll(spec, SearchSpecs.withDefaultSort(pageable, Sort.by(Sort.Direction.DESC, "occurredAt")));
    }
}
