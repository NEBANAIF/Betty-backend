package com.company.erp.purchase;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.purchase.dto.CreatePurchaseOrderRequest;
import com.company.erp.purchase.dto.PurchaseOrderResponse;
import com.company.erp.purchase.dto.ReceivePurchaseRequest;
import com.company.erp.security.BranchAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASE_CREATE') or hasAuthority('PURCHASE_RECEIVE')")
    public PageResponse<PurchaseOrderResponse> search(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return purchaseOrderService.search(scope, branchId, supplierId, status, search, productId, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_CREATE') or hasAuthority('PURCHASE_RECEIVE')")
    public PurchaseOrderResponse get(@PathVariable UUID id) {
        return purchaseOrderService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public PurchaseOrderResponse create(@Valid @RequestBody CreatePurchaseOrderRequest request) {
        return purchaseOrderService.create(request);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public PurchaseOrderResponse submit(@PathVariable UUID id) {
        return purchaseOrderService.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public PurchaseOrderResponse approve(@PathVariable UUID id) {
        return purchaseOrderService.approve(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public PurchaseOrderResponse cancel(@PathVariable UUID id) {
        return purchaseOrderService.cancel(id);
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('PURCHASE_RECEIVE')")
    public PurchaseOrderResponse receive(@PathVariable UUID id, @Valid @RequestBody ReceivePurchaseRequest request) {
        return purchaseOrderService.receive(id, request);
    }
}
