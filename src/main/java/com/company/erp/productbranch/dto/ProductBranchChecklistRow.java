package com.company.erp.productbranch.dto;

import java.util.UUID;

/** One row of a branch's product checklist. allowed = ticked = this product belongs at this branch. */
public record ProductBranchChecklistRow(UUID productId, String productName, String sku, String unit, boolean allowed) {
}
