package com.company.erp.payment;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.loan.LoanRepository;
import com.company.erp.payment.dto.PaymentResponse;
import com.company.erp.sales.SaleRepository;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.ContentAccess;
import com.company.erp.user.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reads payments. Everything here runs in a transaction because a Payment's branch, bank and
 * receiving user are loaded lazily - building the response outside one fails (this service is why
 * the payment lookup by reference works at all).
 */
@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final BranchAccessService branchAccessService;
    private final SaleRepository saleRepository;
    private final LoanRepository loanRepository;
    private final ContentAccess contentAccess;

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> search(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                                PaymentMethod method, UUID bankId,
                                                PaymentReferenceType referenceType,
                                                Instant from, Instant to, String search, Pageable pageable) {
        var branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        var page = paymentRepository.search(branchIds, method, bankId, referenceType, from, to, search, pageable);

        Map<UUID, String> numbers = referenceNumbers(page.getContent());
        boolean showBankAccount = contentAccess.can(Permission.BANK_VIEW);
        return PageResponse.of(page.map(p -> PaymentResponse.of(p, numbers.get(p.getReferenceId()), showBankAccount)));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> byReference(PaymentReferenceType referenceType, UUID referenceId) {
        var payments = paymentRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(referenceType, referenceId);
        // Every payment for one reference belongs to the same branch (the branch the underlying
        // sale/loan/etc. was recorded against) - one access check against that branch covers the list.
        payments.stream().findFirst().ifPresent(p ->
                branchAccessService.resolveReadableBranchId(p.getBranch().getId()));

        Map<UUID, String> numbers = referenceNumbers(payments);
        boolean showBankAccount = contentAccess.can(Permission.BANK_VIEW);
        return payments.stream()
                .map(p -> PaymentResponse.of(p, numbers.get(p.getReferenceId()), showBankAccount))
                .toList();
    }

    /** referenceId -> the sale number to show ("S-000123"), for sale payments and loan repayments. */
    private Map<UUID, String> referenceNumbers(Collection<Payment> payments) {
        Set<UUID> saleIds = new HashSet<>();
        Set<UUID> loanIds = new HashSet<>();
        for (Payment p : payments) {
            if (p.getReferenceType() == PaymentReferenceType.SALE) {
                saleIds.add(p.getReferenceId());
            } else if (p.getReferenceType() == PaymentReferenceType.LOAN_PAYMENT) {
                loanIds.add(p.getReferenceId());
            }
        }

        Map<UUID, String> numbers = new HashMap<>();
        if (!saleIds.isEmpty()) {
            saleRepository.findAllById(saleIds).forEach(sale -> numbers.put(sale.getId(), sale.getSaleNumber()));
        }
        if (!loanIds.isEmpty()) {
            loanRepository.findAllById(loanIds)
                    .forEach(loan -> numbers.put(loan.getId(), loan.getSale().getSaleNumber()));
        }
        return numbers;
    }
}
