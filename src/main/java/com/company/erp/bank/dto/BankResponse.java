package com.company.erp.bank.dto;

import com.company.erp.bank.Bank;

import java.time.Instant;
import java.util.UUID;

/** The full record, including the account number - only sent to roles with the BANK_VIEW switch. */
public record BankResponse(
        UUID id,
        String name,
        String accountName,
        String accountNumber,
        String notes,
        boolean active,
        Instant createdAt
) {
    public static BankResponse from(Bank bank) {
        return new BankResponse(bank.getId(), bank.getName(), bank.getAccountName(), bank.getAccountNumber(),
                bank.getNotes(), bank.isActive(), bank.getCreatedAt());
    }
}
