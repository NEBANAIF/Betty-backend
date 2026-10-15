package com.company.erp.productbranch;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.productbranch.dto.ProductBranchChecklistRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which products belong at which branches. See ProductBranchAvailability's javadoc for the model:
 * a row = allowed, no row = not allowed, and everything starts fully checked.
 */
@Service
@RequiredArgsConstructor
public class ProductBranchAvailabilityService {

    private final ProductBranchAvailabilityRepository repository;
    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;

    /** Called once, right after a product is created: ticks it at every branch that exists so far. */
    @Transactional
    public void seedForNewProduct(UUID productId) {
        List<UUID> branchIds = branchRepository.findAll().stream().map(Branch::getId).toList();
        repository.saveAll(branchIds.stream().map(b -> new ProductBranchAvailability(productId, b)).toList());
    }

    /** Called once, right after a branch is created: ticks every product that exists so far for it. */
    @Transactional
    public void seedForNewBranch(UUID branchId) {
        List<UUID> productIds = productRepository.findAll().stream().map(Product::getId).toList();
        repository.saveAll(productIds.stream().map(p -> new ProductBranchAvailability(p, branchId)).toList());
    }

    @Transactional
    public void setAllowed(UUID productId, UUID branchId, boolean allowed) {
        if (allowed) {
            if (!repository.existsByProductIdAndBranchId(productId, branchId)) {
                repository.save(new ProductBranchAvailability(productId, branchId));
            }
        } else {
            repository.deleteByProductIdAndBranchId(productId, branchId);
        }
    }

    @Transactional(readOnly = true)
    public Set<UUID> allowedProductIds(UUID branchId) {
        return repository.findProductIdsByBranchId(branchId);
    }

    /**
     * Products allowed at EVERY one of the given branches - e.g. a transfer needs the product to
     * belong at both the source and the destination. Null/empty input means "no restriction",
     * returned as null so the caller can tell "everything" apart from "nothing".
     */
    @Transactional(readOnly = true)
    public Set<UUID> allowedProductIdsAtAll(Collection<UUID> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return null;
        }
        Set<UUID> result = null;
        for (UUID branchId : branchIds) {
            Set<UUID> ids = allowedProductIds(branchId);
            if (result == null) {
                result = new HashSet<>(ids);
            } else {
                result.retainAll(ids);
            }
        }
        return result;
    }

    /** The checklist for one branch: every active product, name-sorted, ticked where it's allowed there. */
    @Transactional(readOnly = true)
    public PageResponse<ProductBranchChecklistRow> checklist(UUID branchId, String search, Pageable pageable) {
        Set<UUID> allowed = allowedProductIds(branchId);
        String trimmed = search == null || search.isBlank() ? null : search.trim();
        var page = productRepository.search(true, trimmed, null, pageable)
                .map(p -> new ProductBranchChecklistRow(p.getId(), p.getName(), p.getSku(), p.getUnit(),
                        allowed.contains(p.getId())));
        return PageResponse.of(page);
    }
}
