package com.company.erp.stockhistory;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.security.BranchAccessService;
import com.company.erp.stockhistory.dto.StockHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockHistoryQueryService {

    private final StockHistoryRepository stockHistoryRepository;
    private final BranchAccessService branchAccessService;

    @Transactional(readOnly = true)
    public PageResponse<StockHistoryResponse> search(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                                       UUID productId, StockMovementType movementType,
                                                       Instant from, Instant to, Pageable pageable) {
        var branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        var page = stockHistoryRepository.search(branchIds, productId, movementType, from, to, pageable)
                .map(StockHistoryResponse::from);
        return PageResponse.of(page);
    }
}
