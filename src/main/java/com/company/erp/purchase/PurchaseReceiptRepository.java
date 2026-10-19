package com.company.erp.purchase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceipt, UUID> {

    boolean existsByRequestId(UUID requestId);
}
