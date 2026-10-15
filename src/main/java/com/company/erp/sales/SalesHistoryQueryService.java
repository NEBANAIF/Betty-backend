package com.company.erp.sales;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.sales.dto.SaleResponse;
import com.company.erp.sales.dto.SaleSummaryResponse;
import com.company.erp.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesHistoryQueryService {

    private final SaleRepository saleRepository;
    private final BranchAccessService branchAccessService;

    @Transactional(readOnly = true)
    public PageResponse<SaleSummaryResponse> search(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                                      UUID customerId, PaymentStatus paymentStatus,
                                                      SaleStatus status, Instant from, Instant to, String search,
                                                      Pageable pageable) {
        return search(scope, specificBranchId, customerId, paymentStatus, status, from, to, search, null, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleSummaryResponse> search(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                                      UUID customerId, PaymentStatus paymentStatus,
                                                      SaleStatus status, Instant from, Instant to, String search,
                                                      UUID productId, Pageable pageable) {
        var branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        var found = productId == null
                ? saleRepository.search(branchIds, customerId, paymentStatus, status, from, to, search, pageable)
                : saleRepository.search(branchIds, customerId, paymentStatus, status, from, to, search, productId, pageable);
        return PageResponse.of(found.map(SaleSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public SaleResponse get(UUID id) {
        Sale sale = saleRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Sale", id));
        // Read access to this specific sale is gated by its branch, exactly
        // like every other branch-scoped read.
        branchAccessService.resolveReadableBranchId(sale.getBranch().getId());
        return SaleResponse.from(sale);
    }
}
