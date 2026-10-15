package com.company.erp.inventory;

import com.company.erp.inventory.dto.StockAttentionRow;
import com.company.erp.inventory.dto.StockStatusReportResponse;
import com.company.erp.inventory.dto.StockSummaryResponse;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * In stock / low stock / out of stock per PRODUCT, for the branches the caller
 * selected and is allowed to see. A product's available quantity is its
 * (quantity - reserved) added up over those branches, and a product with no
 * stock row at all counts as 0 available (out of stock). The thresholds are
 * StockStatus's, i.e. the same ones the dashboard uses.
 */
@Service
@RequiredArgsConstructor
public class ProductStockService {

    private static final int ATTENTION_LIMIT = 100;

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final BranchAccessService branchAccessService;

    /** productId -> available quantity over the selected branches. Products with no stock row are absent (= 0). */
    @Transactional(readOnly = true)
    public Map<UUID, Integer> availableByProduct(BranchAccessService.BranchScope scope, UUID branchId) {
        List<UUID> branchIds = branchAccessService.resolveReadableBranchIds(scope, branchId);
        Map<UUID, Integer> available = new HashMap<>();
        if (branchIds.isEmpty()) {
            return available;
        }
        for (Object[] row : inventoryRepository.sumAvailableByProduct(branchIds)) {
            available.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return available;
    }

    /** Ids of the products whose status is {@code status}, given the figures from availableByProduct. */
    @Transactional(readOnly = true)
    public Set<UUID> idsWithStatus(Map<UUID, Integer> available, StockStatus status, boolean activeOnly) {
        return productRepository.findAll().stream()
                .filter(p -> !activeOnly || p.isActive())
                .filter(p -> statusOf(p, available) == status)
                .map(Product::getId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public StockSummaryResponse summary(BranchAccessService.BranchScope scope, UUID branchId) {
        return summarise(activeProducts(), availableByProduct(scope, branchId));
    }

    @Transactional(readOnly = true)
    public StockStatusReportResponse report(BranchAccessService.BranchScope scope, UUID branchId) {
        Map<UUID, Integer> available = availableByProduct(scope, branchId);
        List<Product> products = activeProducts();

        List<StockAttentionRow> needsAttention = products.stream()
                .map(p -> new StockAttentionRow(p.getId(), p.getName(), p.getSku(), p.getUnit(),
                        available.getOrDefault(p.getId(), 0), reorderLevelOf(p), statusOf(p, available)))
                .filter(r -> r.status() != StockStatus.IN_STOCK)
                .sorted(Comparator.comparing((StockAttentionRow r) -> r.status() == StockStatus.OUT_OF_STOCK ? 0 : 1)
                        .thenComparingInt(StockAttentionRow::available)
                        .thenComparing(StockAttentionRow::productName, String.CASE_INSENSITIVE_ORDER))
                .limit(ATTENTION_LIMIT)
                .toList();

        return new StockStatusReportResponse(summarise(products, available), needsAttention);
    }

    private List<Product> activeProducts() {
        return productRepository.findAll().stream().filter(Product::isActive).toList();
    }

    private StockSummaryResponse summarise(List<Product> products, Map<UUID, Integer> available) {
        long in = 0;
        long low = 0;
        long out = 0;
        for (Product p : products) {
            switch (statusOf(p, available)) {
                case IN_STOCK -> in++;
                case LOW_STOCK -> low++;
                case OUT_OF_STOCK -> out++;
            }
        }
        return new StockSummaryResponse(products.size(), in, low, out);
    }

    private StockStatus statusOf(Product product, Map<UUID, Integer> available) {
        return StockStatus.of(available.getOrDefault(product.getId(), 0), reorderLevelOf(product));
    }

    private int reorderLevelOf(Product product) {
        return product.getReorderLevel() == null ? 0 : product.getReorderLevel();
    }
}
