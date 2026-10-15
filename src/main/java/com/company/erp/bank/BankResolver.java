package com.company.erp.bank;

import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.payment.Payment;
import com.company.erp.payment.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The one place that decides how a payment's bank is filled in, so the POS and loan repayments
 * behave identically.
 */
@Component
@RequiredArgsConstructor
public class BankResolver {

    private final BankRepository bankRepository;

    /** A registered bank that is still in use; anything else is refused. */
    public Bank resolveActive(UUID bankId) {
        Bank bank = bankRepository.findById(bankId)
                .orElseThrow(() -> ResourceNotFoundException.of("Bank", bankId));
        if (!bank.isActive()) {
            throw new BusinessRuleViolationException("The bank \"" + bank.getName() + "\" is not active");
        }
        return bank;
    }

    /**
     * Fills in the bank details of a payment.
     * <ul>
     *   <li>BANK with a bank id: links the registered bank and snapshots its name.</li>
     *   <li>BANK without one: only accepted with a typed bank name (older clients); otherwise the
     *       caller must choose a bank.</li>
     *   <li>CASH / CREDIT: no bank at all.</li>
     * </ul>
     * transactionReference is whatever the cashier typed (a transfer or receipt number) - not the
     * bank's own account number, which lives on the Bank record.
     */
    public void attach(Payment payment, PaymentMethod method, UUID bankId, String legacyBankName,
                       String transactionReference) {
        payment.setAccountReference(blankToNull(transactionReference));

        if (method != PaymentMethod.BANK) {
            payment.setBank(null);
            payment.setBankName(null);
            return;
        }
        if (bankId != null) {
            Bank bank = resolveActive(bankId);
            payment.setBank(bank);
            payment.setBankName(bank.getName());
            return;
        }
        if (legacyBankName == null || legacyBankName.isBlank()) {
            throw new BusinessRuleViolationException("Choose the bank this payment went to");
        }
        payment.setBank(null);
        payment.setBankName(legacyBankName.trim());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
