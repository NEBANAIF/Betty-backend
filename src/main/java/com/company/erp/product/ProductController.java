package com.company.erp.product;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.inventory.ProductStockService;
import com.company.erp.inventory.StockStatus;
import com.company.erp.inventory.dto.StockSummaryResponse;
import com.company.erp.product.dto.ProductRequest;
import com.company.erp.product.dto.ProductResponse;
import com.company.erp.security.BranchAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductStockService productStockService;

    // Also the product picker (filter) on the Batches and Stock History pages.
    @GetMapping
    @PreAuthorize("hasAnyAuthority('INVENTORY_VIEW', 'BATCH_VIEW', 'STOCK_HISTORY_VIEW')")
    public PageResponse<ProductResponse> search(
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) StockStatus stockStatus,
            @RequestParam(defaultValue = "false") boolean hideOutOfStock,
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) java.util.List<UUID> requireAtBranches,
            Pageable pageable) {
        return productService.search(activeOnly, search, stockStatus, hideOutOfStock, scope, branchId, requireAtBranches, pageable);
    }

    /** How many active products are in stock, low, and out of stock for the selected branches. */
    @GetMapping("/stock-summary")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public StockSummaryResponse stockSummary(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId) {
        return productStockService.summary(scope, branchId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ProductResponse get(@PathVariable UUID id) {
        return productService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        productService.deactivate(id);
    }
}
