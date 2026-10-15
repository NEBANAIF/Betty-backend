package com.company.erp.batch;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import com.company.erp.batch.dto.BatchResponse;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchQueryService batchQueryService;

    @GetMapping
    @PreAuthorize("hasAuthority('BATCH_VIEW')")
    public PageResponse<BatchResponse> search(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return batchQueryService.search(scope, branchId, productId, from, to, pageable);
    }

    /** In-stock batches of one product at one branch, oldest first - for the POS batch picker. */
    @GetMapping("/available")
    @PreAuthorize("hasAuthority('POS_CHOOSE_BATCH')")
    public List<BatchResponse> available(@RequestParam UUID productId, @RequestParam UUID branchId) {
        return batchQueryService.listAvailable(productId, branchId);
    }
}
