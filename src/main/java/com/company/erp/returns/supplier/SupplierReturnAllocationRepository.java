package com.company.erp.returns.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface SupplierReturnAllocationRepository extends JpaRepository<SupplierReturnAllocation, UUID> {

    /**
     * Total value returned to this supplier (quantityAllocated * unitCost,
     * summed over every allocation on every return ever made against
     * their purchase orders) - what SupplierService.getBalance() nets out
     * of totalPurchased. See SupplierReturn's class-level javadoc.
     */
    @Query("""
            SELECT COALESCE(SUM(a.quantityAllocated * a.unitCost), 0)
            FROM SupplierReturnAllocation a
            WHERE a.supplierReturnItem.supplierReturn.supplier.id = :supplierId
            """)
    BigDecimal sumReturnedValueForSupplier(@Param("supplierId") UUID supplierId);
}
