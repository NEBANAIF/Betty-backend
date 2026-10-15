package com.company.erp.stockchange;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockChangeRequestRepository extends JpaRepository<StockChangeRequest, UUID> {

    Page<StockChangeRequest> findByBranchIdInAndStatusOrderByCreatedAtDesc(
            List<UUID> branchIds, StockChangeStatus status, Pageable pageable);
}
