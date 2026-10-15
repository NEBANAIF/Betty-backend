package com.company.erp.finance;

import com.company.erp.branch.Branch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * THE single entry point for writing to the financial ledger - mirrors
 * StockHistoryService's role exactly. Every service that represents a real
 * financial event (POS checkout, expense creation, loan payment, customer
 * return) calls record(...) in the SAME transaction as the event itself,
 * so the ledger can never show a sale that didn't happen or miss one that
 * did. No other code should construct a FinancialTransaction directly.
 */
@Service
@RequiredArgsConstructor
public class FinancialTransactionService {

    private final FinancialTransactionRepository financialTransactionRepository;

    @Transactional
    public FinancialTransaction record(Branch branch, FinancialTransactionType type, BigDecimal amount,
                                        LocalDate occurredOn, FinancialReferenceType referenceType,
                                        UUID referenceId) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setBranch(branch);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setOccurredOn(occurredOn);
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        return financialTransactionRepository.save(transaction);
    }
}
