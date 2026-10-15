package com.company.erp.stockhistory;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.security.BranchAccessService;
import com.company.erp.stockhistory.dto.StockHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock-history")
@RequiredArgsConstructor
public class StockHistoryController {

    private final StockHistoryQueryService stockHistoryQueryService;

    @GetMapping
    @PreAuthorize("hasAuthority('STOCK_HISTORY_VIEW')")
    public PageResponse<StockHistoryResponse> search(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) StockMovementType movementType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Pageable pageable) {
        return stockHistoryQueryService.search(scope, branchId, productId, movementType, from, to, pageable);
    }
}
