package com.company.erp.stockchange;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.stockchange.dto.RejectStockChangeRequest;
import com.company.erp.stockchange.dto.StockChangeResponse;
import com.company.erp.stockchange.dto.SubmitStockChangeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Manual stock changes held for approval. Entering one needs BATCH_CREATE (add a batch) or
 * STOCK_ADJUST (remove stock) - which of the two is checked in the service, since it depends on
 * the type in the body. Approving needs STOCK_APPROVE. Discarding needs STOCK_APPROVE or being
 * the person who entered it.
 */
@RestController
@RequestMapping("/api/stock-changes")
@RequiredArgsConstructor
public class StockChangeController {

    private final StockChangeService stockChangeService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('BATCH_CREATE', 'STOCK_ADJUST')")
    @ResponseStatus(HttpStatus.CREATED)
    public StockChangeResponse submit(@Valid @RequestBody SubmitStockChangeRequest request) {
        return stockChangeService.submit(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('BATCH_CREATE', 'STOCK_ADJUST', 'STOCK_APPROVE')")
    public PageResponse<StockChangeResponse> list(
            @RequestParam(defaultValue = "PENDING") StockChangeStatus status,
            Pageable pageable) {
        return stockChangeService.list(status, pageable);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('STOCK_APPROVE')")
    public StockChangeResponse approve(@PathVariable UUID id) {
        return stockChangeService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('STOCK_APPROVE', 'BATCH_CREATE', 'STOCK_ADJUST')")
    public StockChangeResponse reject(@PathVariable UUID id,
                                      @Valid @RequestBody(required = false) RejectStockChangeRequest request) {
        return stockChangeService.reject(id, request == null ? null : request.note());
    }
}
