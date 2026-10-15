package com.company.erp.productbranch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

public interface ProductBranchAvailabilityRepository extends JpaRepository<ProductBranchAvailability, UUID> {

    boolean existsByProductIdAndBranchId(UUID productId, UUID branchId);

    @Transactional
    void deleteByProductIdAndBranchId(UUID productId, UUID branchId);

    @Query("SELECT a.productId FROM ProductBranchAvailability a WHERE a.branchId = :branchId")
    Set<UUID> findProductIdsByBranchId(@Param("branchId") UUID branchId);
}
