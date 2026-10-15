package com.company.erp.product.importer;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.DuplicateResourceException;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.product.importer.dto.ProductImportRow;
import com.company.erp.security.BranchAccessService;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.stockhistory.StockReferenceType;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Imports ONE row of the product-import spreadsheet (a product at a branch), in its own
 * transaction, called in a loop by ProductImportService. Each row is
 * self-contained: if the opening-stock part fails validation, the product
 * this row would have created is rolled back too, rather than being left
 * behind at zero stock with no way to re-run the row without hitting a
 * duplicate-SKU error.
 * <p>
 * Deliberately its own transaction per row (not one transaction for the
 * whole file): a typo in row 40 must not undo the 39 good rows before it,
 * and Spring can only give each row a fresh transaction if the method that
 * throws is a *separate* proxied bean call from the loop, not a
 * self-invocation - see ProductImportService, which is NOT @Transactional
 * for exactly that reason.
 */
@Service
@RequiredArgsConstructor
public class ProductImportRowService {

    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final StockMutationService stockMutationService;

    /**
     * One row = one product AT one branch. The first row for a SKU creates the product; a later
     * row with the SAME SKU (and the same name) does not create another product - it only adds
     * that row's opening stock at its branch. That is how one product held in several branches
     * (Power Cable in Warehouse A and Warehouse B) is imported: repeat the Name and SKU on one row
     * per branch, each with its own Branch, Initial Stock and Cost Price. The product's details
     * (unit, reorder level, description) come from the row that created it.
     */
    @Transactional
    public String importRow(ProductImportRow row) {
        String name = required(row.name(), "Name");
        String sku = required(row.sku(), "SKU");
        String unit = required(row.unit(), "Unit");

        Product product = productRepository.findBySkuIgnoreCase(sku).orElse(null);
        boolean existing = product != null;
        if (existing) {
            if (!product.isActive()) {
                throw new DuplicateResourceException("SKU '" + sku + "' belongs to a deleted product - row skipped");
            }
            if (!product.getName().equalsIgnoreCase(name)) {
                throw new DuplicateResourceException("SKU '" + sku + "' already belongs to '" + product.getName()
                        + "'. Use the same name to add stock for another branch, or a different SKU for a different product");
            }
        } else {
            product = new Product();
            product.setName(name);
            product.setSku(sku);
            product.setDescription(row.description());
            product.setUnit(unit);
            product.setReorderLevel(row.reorderLevel() != null ? row.reorderLevel() : 0);
            product.setActive(true);
            product = productRepository.save(product);
        }

        int quantity = row.initialStock() != null ? row.initialStock() : 0;
        if (quantity < 0) {
            throw new BusinessRuleViolationException("Initial Stock can't be negative");
        }
        if (quantity == 0) {
            if (existing) {
                throw new BusinessRuleViolationException("SKU '" + sku
                        + "' already exists and this row has no Initial Stock - nothing to add");
            }
            return "Product created, no opening stock";
        }

        if (row.costPrice() == null) {
            throw new BusinessRuleViolationException(
                    "Cost Price is required when Initial Stock is greater than 0");
        }
        String branchName = required(row.branch(), "Branch");
        Branch branch = branchRepository.findByNameIgnoreCase(branchName)
                .or(() -> branchRepository.findByCodeIgnoreCase(branchName))
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Branch '" + branchName + "' doesn't exist - check the name/code against Branches"));

        // Never trust the sheet's branch as authorization - same rule as every
        // other stock write (see StockChangeService): confirm the current
        // user can actually write to this branch before touching its stock.
        branchAccessService.assertCanWriteToBranch(branch.getId());
        User user = userRepository.findById(branchAccessService.currentUser().getUserId()).orElseThrow();

        stockMutationService.receiveStock(product, branch, quantity, row.costPrice(), LocalDate.now(),
                StockMovementType.ADJUSTMENT, "Bulk import of existing stock", user,
                StockReferenceType.MANUAL_ADJUSTMENT, null);

        return existing
                ? "Existing product - added " + quantity + " " + product.getUnit() + " opening stock at " + branch.getName()
                : "Product created with " + quantity + " " + unit + " opening stock at " + branch.getName();
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(fieldName + " is required");
        }
        return value.trim();
    }
}
