package com.company.erp.stockhistory;

import com.company.erp.batch.ProductBatch;
import com.company.erp.branch.Branch;
import com.company.erp.product.Product;
import com.company.erp.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The only place a StockHistory row is created. Called exclusively from
 * StockMutationService, inside the same transaction as the inventory/batch
 * mutation it's recording - never called standalone, since a history row
 * with no corresponding real movement (or vice versa) would make the
 * ledger untrustworthy.
 */
@Service
@RequiredArgsConstructor
public class StockHistoryService {

    private final StockHistoryRepository stockHistoryRepository;

    @Transactional
    public StockHistory record(Product product, Branch branch, ProductBatch batch,
                                StockMovementType movementType, int quantityChange,
                                int previousQuantity, int newQuantity, String reason,
                                User user, StockReferenceType referenceType, UUID referenceId) {
        StockHistory entry = new StockHistory();
        entry.setProduct(product);
        entry.setBranch(branch);
        entry.setBatch(batch);
        entry.setMovementType(movementType);
        entry.setQuantityChange(quantityChange);
        entry.setPreviousQuantity(previousQuantity);
        entry.setNewQuantity(newQuantity);
        entry.setReason(reason);
        entry.setUser(user);
        entry.setReferenceType(referenceType);
        entry.setReferenceId(referenceId);
        entry.setOccurredAt(Instant.now());
        return stockHistoryRepository.save(entry);
    }
}
