package com.company.erp.inventory.dto;

import java.util.List;

public record StockOverviewResponse(
        List<BranchColumn> branches,
        List<StockOverviewRow> rows
) {
}
