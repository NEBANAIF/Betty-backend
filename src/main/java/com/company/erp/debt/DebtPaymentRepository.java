package com.company.erp.debt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DebtPaymentRepository extends JpaRepository<DebtPayment, UUID> {
    List<DebtPayment> findByDebtIdOrderByPaymentDateDescCreatedAtDesc(UUID debtId);
}
