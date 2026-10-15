package com.company.erp.sales;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.sales.dto.SaleResponse;
import com.company.erp.sales.dto.SaleSummaryResponse;
import com.company.erp.sales.dto.VoidSaleRequest;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.ContentAccess;
import com.company.erp.user.Permission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Sales History (spec section 13) - deliberately separate from POS
 * (spec section 11 is explicit: "POS is NOT Sales History"). Two distinct
 * reversal actions live on this screen: Return (CustomerReturnController),
 * for a valid sale whose goods physically came back, full or partial; and
 * Void (SaleVoidService, below), for a sale that should never have
 * existed at all - see that class's javadoc for the full reasoning.
 */
@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SalesHistoryController {

    private final SalesHistoryQueryService salesHistoryQueryService;
    private final ContentAccess contentAccess;
    private final SaleVoidService saleVoidService;

    @GetMapping
    @PreAuthorize("hasAuthority('SALES_VIEW')")
    public PageResponse<SaleSummaryResponse> search(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID productId,
            Pageable pageable) {
        return salesHistoryQueryService.search(scope, branchId, customerId, paymentStatus, status, from, to, search, productId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_VIEW')")
    public SaleResponse get(@PathVariable UUID id) {
        SaleResponse sale = salesHistoryQueryService.get(id);
        return contentAccess.can(Permission.VIEW_PROFIT) ? sale : sale.withoutProfit();
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('SALES_VOID')")
    public SaleResponse voidSale(@PathVariable UUID id, @Valid @RequestBody VoidSaleRequest request) {
        return saleVoidService.voidSale(id, request.reason());
    }
}
