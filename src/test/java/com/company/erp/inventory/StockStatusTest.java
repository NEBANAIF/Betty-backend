package com.company.erp.inventory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockStatusTest {

    @Test
    void nothingAvailableIsOutOfStockEvenWhenTheReorderLevelIsZero() {
        assertThat(StockStatus.of(0, 0)).isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(StockStatus.of(0, 10)).isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(StockStatus.of(-2, 10)).isEqualTo(StockStatus.OUT_OF_STOCK);
    }

    @Test
    void atOrBelowTheReorderLevelIsLowStockOnceThereIsSomethingLeft() {
        assertThat(StockStatus.of(1, 10)).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(StockStatus.of(10, 10)).isEqualTo(StockStatus.LOW_STOCK);
    }

    @Test
    void aboveTheReorderLevelIsInStock() {
        assertThat(StockStatus.of(11, 10)).isEqualTo(StockStatus.IN_STOCK);
        assertThat(StockStatus.of(1, 0)).isEqualTo(StockStatus.IN_STOCK);
    }
}
