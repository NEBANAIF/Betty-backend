package com.company.erp.batch;

import com.company.erp.common.exception.BusinessRuleViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Fixes the cost price on the oldest in-stock batch of a product - the
 * "correct it later" half of Settings > Inventory > Lock cost price.
 * <p>
 * When that setting is on, a new product's opening batch is created at
 * cost 0 because whoever is recording stock doesn't know the real cost yet
 * (see the New Product form). This is how someone
 * with STOCK_ADJUST goes back afterwards and puts the real number in, once
 * they've found it out - it edits the batch itself, in place, rather than
 * creating a correcting movement, because nothing about the quantity on
 * hand changed, only what it's recorded as having cost.
 * <p>
 * Deliberately targets the OLDEST batch still in stock, not "all of
 * them" - that is the one FIFO will cost the next sale at (ProductCostRow
 * .oldestCost), i.e. the exact figure the Edit Product screen shows and
 * lets you fix. A product with several batches (because real purchases
 * came in after the unknown-cost opening one) has more than one cost by
 * design - see ProductBatch's class comment - and this method only ever
 * touches the single oldest one, matching what "initial cost" means.
 */
@Service
@RequiredArgsConstructor
public class BatchCostCorrectionService {

    private final ProductBatchRepository productBatchRepository;

    @Transactional
    public BigDecimal correctOldestCost(UUID productId, List<UUID> branchIds, BigDecimal newCostPrice) {
        List<ProductBatch> batches = productBatchRepository.findConsumableForUpdate(productId, branchIds);
        if (batches.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "This product has no stock on hand in the selected branches, so there's no batch cost to correct.");
        }
        ProductBatch oldest = batches.get(0);
        oldest.setCostPrice(newCostPrice);
        return newCostPrice;
    }
}
