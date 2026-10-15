package com.company.erp.inventory;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.inventory.dto.BranchColumn;
import com.company.erp.inventory.dto.StockOverviewResponse;
import com.company.erp.inventory.dto.StockOverviewRow;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockOverviewService {

    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
    private final InventoryRepository inventoryRepository;
    private final BranchAccessService branchAccessService;

    @Transactional(readOnly = true)
    public StockOverviewResponse getOverview(BranchAccessService.BranchScope scope, UUID specificBranchId) {
        List<UUID> branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);

        List<Branch> branches = branchRepository.findAllById(branchIds);
        // Preserve a stable order (by name) regardless of the order the IDs came back in.
        branches.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        List<BranchColumn> columns = branches.stream()
                .map(b -> new BranchColumn(b.getId(), b.getName(), b.getType().name()))
                .toList();

        Map<UUID, Map<UUID, Integer>> quantityByProductThenBranch = new HashMap<>();
        for (Inventory inv : inventoryRepository.findByBranchIdIn(branchIds)) {
            quantityByProductThenBranch
                    .computeIfAbsent(inv.getProduct().getId(), k -> new HashMap<>())
                    .put(inv.getBranch().getId(), inv.getQuantity());
        }

        List<Product> activeProducts = productRepository.findAll().stream()
                .filter(Product::isActive)
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();

        List<StockOverviewRow> rows = activeProducts.stream().map(product -> {
            Map<UUID, Integer> perBranch = quantityByProductThenBranch.getOrDefault(product.getId(), Map.of());
            Map<UUID, Integer> filled = new HashMap<>();
            int total = 0;
            for (UUID branchId : branchIds) {
                int qty = perBranch.getOrDefault(branchId, 0);
                filled.put(branchId, qty);
                total += qty;
            }
            return new StockOverviewRow(
                    product.getId(), product.getName(), product.getSku(), filled, total);
        }).toList();

        return new StockOverviewResponse(columns, rows);
    }
}
