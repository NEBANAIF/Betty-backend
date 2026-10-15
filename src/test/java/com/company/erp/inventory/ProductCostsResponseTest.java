package com.company.erp.inventory;

import com.company.erp.inventory.dto.ProductCostRow;
import com.company.erp.inventory.dto.ProductCostsResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCostsResponseTest {

    private final ProductCostsResponse full = new ProductCostsResponse(new BigDecimal("340.00"), List.of(
            new ProductCostRow(UUID.randomUUID(), 30, new BigDecimal("10.00"), new BigDecimal("12.00"), new BigDecimal("340.00"))));

    @Test
    void withoutTheCostPermissionCostsAreLeftOutButValueRemains() {
        var limited = full.limitedTo(false, true);

        assertThat(limited.totalStockValue()).isEqualByComparingTo("340.00");
        ProductCostRow row = limited.rows().get(0);
        assertThat(row.quantityOnHand()).isEqualTo(30);
        assertThat(row.oldestCost()).isNull();
        assertThat(row.latestCost()).isNull();
        assertThat(row.stockValue()).isEqualByComparingTo("340.00");
    }

    @Test
    void withoutTheInventoryValuePermissionValueAndTotalAreLeftOut() {
        var limited = full.limitedTo(true, false);

        assertThat(limited.totalStockValue()).isNull();
        ProductCostRow row = limited.rows().get(0);
        assertThat(row.oldestCost()).isEqualByComparingTo("10.00");
        assertThat(row.latestCost()).isEqualByComparingTo("12.00");
        assertThat(row.stockValue()).isNull();
    }
}
