package com.company.erp.purchase;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Join;
import java.time.LocalDate;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Root;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID>, JpaSpecificationExecutor<PurchaseOrder> {

    boolean existsByOrderNumber(String orderNumber);

    long countByBranchId(UUID branchId);

    default Page<PurchaseOrder> search(List<UUID> branchIds, UUID supplierId, PurchaseOrderStatus status,
                                       String search, Pageable pageable) {
        return search(branchIds, supplierId, status, search, null, null, null, pageable);
    }

    /** Same again, plus an optional product (orders that carry it) and an order-date range. */
    default Page<PurchaseOrder> search(List<UUID> branchIds, UUID supplierId, PurchaseOrderStatus status,
                                       String search, UUID productId, LocalDate from, LocalDate to,
                                       Pageable pageable) {
        return search(branchIds, new PurchaseSearchCriteria(supplierId, status, search, productId, from, to,
                null, null, null, null, null, null, null, null), pageable);
    }

    /** The full Purchases search: the filters above plus type, countries, warehouse, shipment status and arrival. */
    default Page<PurchaseOrder> search(List<UUID> branchIds, PurchaseSearchCriteria c, Pageable pageable) {
        LocalDate today = LocalDate.now();
        Specification<PurchaseOrder> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(SearchSpecs.idIn(root.get("branch").get("id"), branchIds, cb));
            if (c.supplierId() != null) {
                p.add(cb.equal(root.get("supplier").get("id"), c.supplierId()));
            }
            if (c.status() != null) {
                p.add(cb.equal(root.get("status"), c.status()));
            }
            if (SearchSpecs.hasText(c.search())) {
                Join<Object, Object> supplier = root.join("supplier", JoinType.LEFT);
                p.add(cb.or(
                        SearchSpecs.containsIgnoreCase(root.<String>get("orderNumber"), c.search().trim(), cb),
                        SearchSpecs.containsIgnoreCase(supplier.<String>get("name"), c.search().trim(), cb)));
            }
            if (c.productId() != null) {
                Subquery<UUID> withProduct = query.subquery(UUID.class);
                Root<PurchaseOrderItem> item = withProduct.from(PurchaseOrderItem.class);
                withProduct.select(item.get("purchaseOrder").<UUID>get("id"))
                        .where(cb.equal(item.get("product").get("id"), c.productId()));
                p.add(root.<UUID>get("id").in(withProduct));
            }
            if (c.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("orderDate"), c.from()));
            }
            if (c.to() != null) {
                p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("orderDate"), c.to()));
            }
            if (c.purchaseType() != null) {
                p.add(cb.equal(root.get("purchaseType"), c.purchaseType()));
            }
            if (SearchSpecs.hasText(c.originCountry())) {
                p.add(cb.equal(cb.lower(root.<String>get("originCountry")), c.originCountry().trim().toLowerCase()));
            }
            if (SearchSpecs.hasText(c.destinationCountry())) {
                p.add(cb.equal(cb.lower(root.<String>get("destinationCountry")),
                        c.destinationCountry().trim().toLowerCase()));
            }
            if (SearchSpecs.hasText(c.currentCountry())) {
                p.add(cb.equal(cb.lower(root.<String>get("currentCountry")), c.currentCountry().trim().toLowerCase()));
            }
            if (c.warehouseId() != null) {
                p.add(cb.equal(root.get("branch").get("id"), c.warehouseId()));
            }
            if (SearchSpecs.hasText(c.shipmentStatus())) {
                p.add(cb.equal(root.get("purchaseType"), PurchaseType.GLOBAL));
                if ("DELAYED".equalsIgnoreCase(c.shipmentStatus().trim())) {
                    // Late = not arrived and past the expected date, or arrived after it. Cancelled orders are never late,
                    // and neither are orders already fully received without the route being kept up to date.
                    Predicate stillOnTheWay = cb.and(
                            cb.notEqual(root.get("shipmentStatus"), ShipmentStatus.ARRIVED),
                            cb.notEqual(root.get("status"), PurchaseOrderStatus.RECEIVED),
                            cb.lessThan(root.<LocalDate>get("expectedArrivalDate"), today));
                    Predicate arrivedLate = cb.and(
                            cb.equal(root.get("shipmentStatus"), ShipmentStatus.ARRIVED),
                            cb.greaterThan(root.<LocalDate>get("actualArrivalDate"),
                                    root.<LocalDate>get("expectedArrivalDate")));
                    p.add(cb.notEqual(root.get("status"), PurchaseOrderStatus.CANCELLED));
                    p.add(cb.or(stillOnTheWay, arrivedLate));
                } else {
                    p.add(cb.equal(root.get("shipmentStatus"),
                            ShipmentStatus.valueOf(c.shipmentStatus().trim().toUpperCase())));
                }
            }
            if (c.arrivalMonth() != null) {
                p.add(cb.equal(root.<Integer>get("arrivalMonth"), c.arrivalMonth()));
            }
            if (c.arrivalYear() != null) {
                p.add(cb.equal(root.<Integer>get("arrivalYear"), c.arrivalYear()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return findAll(spec, SearchSpecs.withDefaultSort(pageable,
                Sort.by(Sort.Order.desc("orderDate"), Sort.Order.desc("createdAt"))));
    }

    @Query("""
            SELECT COALESCE(SUM(i.receivedQuantity * i.unitCost * i.purchaseOrder.exchangeRate), 0)
            FROM PurchaseOrderItem i
            WHERE i.purchaseOrder.supplier.id = :supplierId
            """)
    BigDecimal sumReceivedValueForSupplier(@Param("supplierId") UUID supplierId);
}
