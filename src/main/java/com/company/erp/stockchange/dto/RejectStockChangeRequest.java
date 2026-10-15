package com.company.erp.stockchange.dto;

import jakarta.validation.constraints.Size;

/** Optional explanation when discarding a held stock change. */
public record RejectStockChangeRequest(@Size(max = 500) String note) {
}
