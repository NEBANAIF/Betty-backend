package com.company.erp.returns.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SupplierReturnItemRepository extends JpaRepository<SupplierReturnItem, UUID> {

    /** How many units have already been returned against this exact PurchaseOrderItem line, across all prior returns. */
    @Query("""
            SELECT COALESCE(SUM(i.quantity), 0) FROM SupplierReturnItem i
            WHERE i.purchaseOrderItem.id = :purchaseOrderItemId
            """)
    int sumReturnedForPurchaseOrderItem(@Param("purchaseOrderItemId") UUID purchaseOrderItemId);
}
