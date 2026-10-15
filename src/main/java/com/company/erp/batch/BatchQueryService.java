package com.company.erp.batch;

import java.time.LocalDate;
import com.company.erp.batch.dto.BatchResponse;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.ContentAccess;
import com.company.erp.user.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BatchQueryService {

    private final ProductBatchRepository productBatchRepository;
    private final BranchAccessService branchAccessService;
    private final ContentAccess contentAccess;

    @Transactional(readOnly = true)
    public PageResponse<BatchResponse> search(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                               UUID productId, LocalDate from, LocalDate to, Pageable pageable) {
        var branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        boolean showCost = contentAccess.can(Permission.VIEW_COSTS);
        var page = productBatchRepository.search(branchIds, productId, from, to, pageable)
                .map(b -> showCost ? BatchResponse.from(b) : BatchResponse.from(b).withoutCost());
        return PageResponse.of(page);
    }

    /**
     * Batches of a product that still have stock at ONE branch, oldest first -
     * what the POS offers when a cashier wants to sell from a specific batch.
     * A non-admin always gets their own branch regardless of the id sent
     * (same rule as every other read here); a super admin gets the branch asked for.
     */
    @Transactional(readOnly = true)
    public List<BatchResponse> listAvailable(UUID productId, UUID branchId) {
        var branchIds = branchAccessService.resolveReadableBranchIds(
                BranchAccessService.BranchScope.SPECIFIC, branchId);
        boolean showCost = contentAccess.can(Permission.VIEW_COSTS);
        return productBatchRepository.findAvailable(productId, branchIds.get(0)).stream()
                .map(b -> showCost ? BatchResponse.from(b) : BatchResponse.from(b).withoutCost())
                .toList();
    }
}
