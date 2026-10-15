package com.company.erp.bank;

import com.company.erp.bank.dto.BankOptionResponse;
import com.company.erp.bank.dto.BankRequest;
import com.company.erp.bank.dto.BankResponse;
import com.company.erp.common.exception.DuplicateResourceException;
import com.company.erp.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BankService {

    private final BankRepository bankRepository;

    @Transactional(readOnly = true)
    public List<BankResponse> list() {
        return bankRepository.findAllByOrderByNameAscAccountNumberAsc().stream().map(BankResponse::from).toList();
    }

    /** Active banks only, without account numbers - for the POS / repayment dropdowns. */
    @Transactional(readOnly = true)
    public List<BankOptionResponse> options() {
        return bankRepository.findByActiveTrueOrderByNameAscAccountNumberAsc().stream()
                .map(BankOptionResponse::from).toList();
    }

    @Transactional
    public BankResponse create(BankRequest request) {
        String name = request.name().trim();
        String accountNumber = request.accountNumber().trim();
        if (bankRepository.existsByNameIgnoreCaseAndAccountNumber(name, accountNumber)) {
            throw new DuplicateResourceException(duplicateMessage(name, accountNumber));
        }

        Bank bank = new Bank();
        apply(bank, name, accountNumber, request);
        return BankResponse.from(bankRepository.save(bank));
    }

    @Transactional
    public BankResponse update(UUID id, BankRequest request) {
        Bank bank = findOrThrow(id);
        String name = request.name().trim();
        String accountNumber = request.accountNumber().trim();
        if (bankRepository.existsByNameIgnoreCaseAndAccountNumberAndIdNot(name, accountNumber, id)) {
            throw new DuplicateResourceException(duplicateMessage(name, accountNumber));
        }

        apply(bank, name, accountNumber, request);
        return BankResponse.from(bank);
    }

    /** Banks are switched off rather than deleted, because payments refer to them. */
    @Transactional
    public BankResponse setActive(UUID id, boolean active) {
        Bank bank = findOrThrow(id);
        bank.setActive(active);
        return BankResponse.from(bank);
    }

    private void apply(Bank bank, String name, String accountNumber, BankRequest request) {
        bank.setName(name);
        bank.setAccountNumber(accountNumber);
        bank.setAccountName(blankToNull(request.accountName()));
        bank.setNotes(blankToNull(request.notes()));
    }

    private Bank findOrThrow(UUID id) {
        return bankRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Bank", id));
    }

    private String duplicateMessage(String name, String accountNumber) {
        return "Account " + accountNumber + " at " + name + " is already registered";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
