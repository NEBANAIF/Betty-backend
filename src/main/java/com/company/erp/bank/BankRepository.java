package com.company.erp.bank;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BankRepository extends JpaRepository<Bank, UUID> {

    boolean existsByNameIgnoreCaseAndAccountNumber(String name, String accountNumber);

    boolean existsByNameIgnoreCaseAndAccountNumberAndIdNot(String name, String accountNumber, UUID id);

    List<Bank> findAllByOrderByNameAscAccountNumberAsc();

    List<Bank> findByActiveTrueOrderByNameAscAccountNumberAsc();
}
