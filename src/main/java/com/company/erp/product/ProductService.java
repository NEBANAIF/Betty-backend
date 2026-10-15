package com.company.erp.product;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.common.exception.DuplicateResourceException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.inventory.ProductStockService;
import com.company.erp.inventory.StockStatus;
import com.company.erp.productbranch.ProductBranchAvailabilityService;
import com.company.erp.product.dto.ProductRequest;
import com.company.erp.product.dto.ProductResponse;
import com.company.erp.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductStockService productStockService;
    private final ProductBranchAvailabilityService productBranchAvailabilityService;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(boolean activeOnly, String search, StockStatus stockStatus,
                                                boolean hideOutOfStock, BranchAccessService.BranchScope scope,
                                                UUID branchId, Collection<UUID> requireAtBranches, Pageable pageable) {
        // Stock is worked out for the branches the caller picked (and may see); the same
        // figures drive the status filter, hideOutOfStock, and the status shown on each row.
        Map<UUID, Integer> available = productStockService.availableByProduct(scope, branchId);
        Set<UUID> idFilter = stockStatus == null
                ? null
                : productStockService.idsWithStatus(available, stockStatus, activeOnly);
        if (hideOutOfStock) {
            Set<UUID> outOfStock = productStockService.idsWithStatus(available, StockStatus.OUT_OF_STOCK, activeOnly);
            if (idFilter == null) {
                // Everything except out-of-stock: start from every active product and subtract.
                idFilter = productRepository.findAll().stream()
                        .filter(p -> !activeOnly || p.isActive())
                        .map(Product::getId)
                        .filter(id -> !outOfStock.contains(id))
                        .collect(java.util.stream.Collectors.toSet());
            } else {
                idFilter.removeAll(outOfStock);
            }
        }

        // requireAtBranches is the branch-availability checklist (see ProductBranchAvailability):
        // a product with no row for one of these branches is invisible here, everywhere this
        // method is used to search - Products list, POS, Purchases, Batches, Transfers all go
        // through it. Independent of hideOutOfStock/stockStatus, which are about stock levels.
        Set<UUID> allowedEverywhere = productBranchAvailabilityService.allowedProductIdsAtAll(requireAtBranches);
        if (allowedEverywhere != null) {
            if (idFilter == null) {
                idFilter = allowedEverywhere;
            } else {
                idFilter.retainAll(allowedEverywhere);
            }
        }

        var page = productRepository.search(activeOnly, blankToNull(search), idFilter, pageable)
                .map(p -> ProductResponse.from(p, available.getOrDefault(p.getId(), 0)));
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return ProductResponse.from(findOrThrow(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySkuIgnoreCase(request.sku())) {
            throw new DuplicateResourceException("A product with SKU '" + request.sku() + "' already exists");
        }
        Product product = new Product();
        applyRequest(product, request);
        product.setActive(true);

        Product saved = productRepository.save(product);
        // Starts fully checked: allowed at every branch that exists so far (see
        // ProductBranchAvailability's javadoc). Someone unchecks it per-branch afterwards.
        productBranchAvailabilityService.seedForNewProduct(saved.getId());
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = findOrThrow(id);

        if (!product.getSku().equalsIgnoreCase(request.sku())
                && productRepository.existsBySkuIgnoreCase(request.sku())) {
            throw new DuplicateResourceException("A product with SKU '" + request.sku() + "' already exists");
        }

        applyRequest(product, request);

        return ProductResponse.from(product);
    }

    /**
     * Soft delete only - a product with historical batches/sales can never
     * be hard-deleted without destroying cost lineage and sales history.
     * "Delete" means active=false, which hides it from POS/new purchases
     * while every past transaction referencing it stays intact.
     */
    @Transactional
    public void deactivate(UUID id) {
        Product product = findOrThrow(id);
        product.setActive(false);
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.name());
        product.setSku(request.sku());
        product.setDescription(request.description());
        product.setUnit(request.unit());
        product.setReorderLevel(request.reorderLevel() != null ? request.reorderLevel() : 0);
    }

    private Product findOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
