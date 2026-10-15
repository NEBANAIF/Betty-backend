package com.company.erp.inventory;

import com.company.erp.batch.ProductBatch;
import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.inventory.dto.ProductCostRow;
import com.company.erp.inventory.dto.ProductCostsResponse;
import com.company.erp.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Answers "what did the stock I'm holding actually cost me?" per product.
 * <p>
 * Product has no cost field on purpose: the same product bought a week ago
 * at 10 and today at 12 is two batches with two costs. Everything here is
 * derived from those batches (never stored), the same source FIFO uses to
 * cost a sale - so the inventory value shown here and the profit computed
 * at the till can never disagree about what a unit cost.
 */
@Service
@RequiredArgsConstructor
public class ProductCostService {

    private final ProductBatchRepository productBatchRepository;
    private final BranchAccessService branchAccessService;

    @Transactional(readOnly = true)
    public ProductCostsResponse getCosts(BranchAccessService.BranchScope scope, UUID specificBranchId) {
        List<UUID> branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        if (branchIds.isEmpty()) {
            return new ProductCostsResponse(money(BigDecimal.ZERO), List.of());
        }

        // Repository returns batches oldest-first (FIFO order), so within each
        // product the first batch is the oldest and the last is the newest.
        Map<UUID, List<ProductBatch>> batchesByProduct = new LinkedHashMap<>();
        for (ProductBatch batch : productBatchRepository.findInStockForBranches(branchIds)) {
            batchesByProduct.computeIfAbsent(batch.getProduct().getId(), id -> new ArrayList<>()).add(batch);
        }

        List<ProductCostRow> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<UUID, List<ProductBatch>> entry : batchesByProduct.entrySet()) {
            List<ProductBatch> batches = entry.getValue();

            int quantity = 0;
            BigDecimal value = BigDecimal.ZERO;
            for (ProductBatch batch : batches) {
                quantity += batch.getRemainingQuantity();
                value = value.add(batch.getCostPrice().multiply(BigDecimal.valueOf(batch.getRemainingQuantity())));
            }

            rows.add(new ProductCostRow(
                    entry.getKey(),
                    quantity,
                    batches.get(0).getCostPrice(),
                    batches.get(batches.size() - 1).getCostPrice(),
                    money(value)));
            total = total.add(value);
        }

        return new ProductCostsResponse(money(total), rows);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
