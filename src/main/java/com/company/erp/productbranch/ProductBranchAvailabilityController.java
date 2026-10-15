package com.company.erp.productbranch;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.productbranch.dto.ProductBranchChecklistRow;
import com.company.erp.productbranch.dto.SetProductBranchAvailabilityRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Managing which products belong at which branch - the same permission as managing the branches themselves. */
@RestController
@RequestMapping("/api/product-branches")
@RequiredArgsConstructor
public class ProductBranchAvailabilityController {

    private final ProductBranchAvailabilityService service;

    @GetMapping
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public PageResponse<ProductBranchChecklistRow> checklist(
            @RequestParam UUID branchId,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return service.checklist(branchId, search, pageable);
    }

    @PutMapping("/{productId}/{branchId}")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setAllowed(@PathVariable UUID productId, @PathVariable UUID branchId,
                           @Valid @RequestBody SetProductBranchAvailabilityRequest request) {
        service.setAllowed(productId, branchId, request.allowed());
    }
}
