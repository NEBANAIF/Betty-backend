package com.company.erp.batch;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of a FIFO consumption result: "N units came out of batch X at
 * unit cost Y". A single sale/transfer/adjustment of quantity Q against a
 * product+branch may span multiple batches, so consuming Q units produces
 * a List<BatchConsumption> whose quantities sum to Q.
 */
public record BatchConsumption(
        UUID batchId,
        String batchNumber,
        int quantityConsumed,
        BigDecimal unitCost
) {
    public BigDecimal lineCost() {
        return unitCost.multiply(BigDecimal.valueOf(quantityConsumed));
    }
}
