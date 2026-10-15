package com.company.erp.transfer;

import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.security.BranchAccessService;
import com.company.erp.transfer.dto.CreateTransferRequest;
import com.company.erp.transfer.dto.RejectTransferRequest;
import com.company.erp.transfer.dto.TransferResponse;
import com.company.erp.transfer.dto.TransferSummaryResponse;
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

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @GetMapping
    @PreAuthorize("hasAuthority('TRANSFER_CREATE') or hasAuthority('TRANSFER_APPROVE') or hasAuthority('TRANSFER_RECEIVE')")
    public PageResponse<TransferSummaryResponse> search(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Pageable pageable) {
        return stockTransferService.search(scope, branchId, status, search, productId, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TRANSFER_CREATE') or hasAuthority('TRANSFER_APPROVE') or hasAuthority('TRANSFER_RECEIVE')")
    public TransferResponse get(@PathVariable UUID id) {
        return stockTransferService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TRANSFER_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse create(@Valid @RequestBody CreateTransferRequest request) {
        return stockTransferService.create(request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('TRANSFER_APPROVE')")
    public TransferResponse approve(@PathVariable UUID id) {
        return stockTransferService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('TRANSFER_APPROVE')")
    public TransferResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectTransferRequest request) {
        return stockTransferService.reject(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('TRANSFER_CREATE')")
    public TransferResponse cancel(@PathVariable UUID id) {
        return stockTransferService.cancel(id);
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAuthority('TRANSFER_APPROVE')")
    public TransferResponse dispatch(@PathVariable UUID id) {
        return stockTransferService.dispatch(id);
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('TRANSFER_RECEIVE')")
    public TransferResponse receive(@PathVariable UUID id) {
        return stockTransferService.receive(id);
    }
}
