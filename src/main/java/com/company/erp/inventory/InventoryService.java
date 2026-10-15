package com.company.erp.inventory;

import com.company.erp.branch.Branch;
import com.company.erp.common.exception.InsufficientStockException;
import com.company.erp.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    /**
     * Row-locked get-or-create. Always call this (never the plain
     * findByProductIdAndBranchId) from a mutation path, so a product's
     * very first stock receipt at a branch can't race with a concurrent
     * first receipt and create two Inventory rows for the same pair
     * (the DB unique constraint would catch that too, but this avoids
     * relying on catching a constraint-violation exception as control flow).
     */
    @Transactional
    public Inventory getOrCreateForUpdate(Product product, Branch branch) {
        return inventoryRepository.findForUpdate(product.getId(), branch.getId())
                .orElseGet(() -> {
                    Inventory inventory = new Inventory();
                    inventory.setProduct(product);
                    inventory.setBranch(branch);
                    inventory.setQuantity(0);
                    inventory.setReservedQuantity(0);
                    return inventoryRepository.save(inventory);
                });
    }

    /** Returns the previous quantity, for the caller to pass into the StockHistory entry. */
    @Transactional
    public int increase(Inventory inventory, int amount) {
        int previous = inventory.getQuantity();
        inventory.setQuantity(previous + amount);
        return previous;
    }

    /** Returns the previous quantity. Throws if the decrease would take available stock negative. */
    @Transactional
    public int decrease(Inventory inventory, int amount) {
        int previous = inventory.getQuantity();
        if (inventory.getAvailableQuantity() < amount) {
            throw new InsufficientStockException(
                    "Insufficient available stock: requested " + amount +
                            " but only " + inventory.getAvailableQuantity() + " available");
        }
        inventory.setQuantity(previous - amount);
        return previous;
    }
}
