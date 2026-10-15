package com.company.erp.batch;

import com.company.erp.branch.Branch;
import com.company.erp.product.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The only place a ProductBatch is ever created. Called by:
 * - Purchase receiving (Phase 5) - one batch per received purchase-order line
 * - Stock Transfer receiving (Phase 8) - one batch at the destination
 *   branch, preserving the source batch's cost price
 * - Customer Return restocking (Phase 9), when a return can't be merged
 *   back into its original batch
 * - Manual stock increase adjustments (this phase)
 *
 * Never called directly by a controller - always via the orchestrating
 * StockMutationService so batch creation, inventory aggregate update, and
 * stock history all happen inside the same transaction.
 */
@Service
public class BatchService {

    private final ProductBatchRepository productBatchRepository;

    public BatchService(ProductBatchRepository productBatchRepository) {
        this.productBatchRepository = productBatchRepository;
    }

    public ProductBatch createBatch(Product product, Branch branch, int quantity,
                                     BigDecimal costPrice, LocalDate receivedDate) {
        return createBatch(product, branch, quantity, costPrice, receivedDate, null, null);
    }

    public ProductBatch createBatch(Product product, Branch branch, int quantity, BigDecimal costPrice,
                                     LocalDate receivedDate, java.util.UUID sourcePurchaseOrderItemId) {
        return createBatch(product, branch, quantity, costPrice, receivedDate, sourcePurchaseOrderItemId, null);
    }

    public ProductBatch createBatch(Product product, Branch branch, int quantity, BigDecimal costPrice,
                                     LocalDate receivedDate, java.util.UUID sourcePurchaseOrderItemId,
                                     LocalDate expiryDate) {
        ProductBatch batch = new ProductBatch();
        batch.setBatchNumber(generateBatchNumber(branch));
        batch.setProduct(product);
        batch.setBranch(branch);
        batch.setQuantity(quantity);
        batch.setRemainingQuantity(quantity);
        batch.setCostPrice(costPrice);
        batch.setReceivedDate(receivedDate);
        batch.setSourcePurchaseOrderItemId(sourcePurchaseOrderItemId);
        batch.setExpiryDate(expiryDate);
        return productBatchRepository.save(batch);
    }

    private String generateBatchNumber(Branch branch) {
        String stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String candidate;
        do {
            int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
            candidate = "BATCH-" + branch.getCode() + "-" + stamp + "-" + suffix;
        } while (productBatchRepository.existsByBatchNumber(candidate));
        return candidate;
    }
}
