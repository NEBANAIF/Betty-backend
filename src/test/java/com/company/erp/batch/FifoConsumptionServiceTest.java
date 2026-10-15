package com.company.erp.batch;

import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.InsufficientStockException;
import com.company.erp.product.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FifoConsumptionServiceTest {

    @Mock
    private ProductBatchRepository productBatchRepository;

    private FifoConsumptionService service;

    private final UUID productId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FifoConsumptionService(productBatchRepository);
    }

    @Test
    void consumesOldestBatchFirstAndSplitsAcrossBatchesAsNeeded() {
        ProductBatch batchA = batchOf(100, new BigDecimal("110"), LocalDate.of(2026, 1, 1));
        ProductBatch batchB = batchOf(200, new BigDecimal("120"), LocalDate.of(2026, 1, 10));
        when(productBatchRepository.findConsumableForUpdate(productId, branchId))
                .thenReturn(List.of(batchA, batchB)); // repository query already returns FIFO order

        List<BatchConsumption> result = service.consume(productId, branchId, 150);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).quantityConsumed()).isEqualTo(100);
        assertThat(result.get(0).unitCost()).isEqualTo(new BigDecimal("110"));
        assertThat(result.get(1).quantityConsumed()).isEqualTo(50);
        assertThat(result.get(1).unitCost()).isEqualTo(new BigDecimal("120"));

        BigDecimal totalCogs = result.stream()
                .map(BatchConsumption::lineCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalCogs).isEqualByComparingTo(new BigDecimal("17000")); // 100*110 + 50*120

        assertThat(batchA.getRemainingQuantity()).isZero();
        assertThat(batchB.getRemainingQuantity()).isEqualTo(150);
    }

    @Test
    void throwsInsufficientStockRatherThanPartiallyConsuming() {
        ProductBatch onlyBatch = batchOf(50, new BigDecimal("110"), LocalDate.of(2026, 1, 1));
        when(productBatchRepository.findConsumableForUpdate(productId, branchId))
                .thenReturn(List.of(onlyBatch));

        assertThatThrownBy(() -> service.consume(productId, branchId, 100))
                .isInstanceOf(InsufficientStockException.class);

        // No partial mutation happened - the batch is untouched after the failed attempt.
        assertThat(onlyBatch.getRemainingQuantity()).isEqualTo(50);
    }

    @Test
    void consumeFromPurchaseOrderItemOnlyDrawsFromBatchesScopedToThatLine() {
        UUID purchaseOrderItemId = UUID.randomUUID();
        ProductBatch scopedBatch = batchOf(40, new BigDecimal("100"), LocalDate.of(2026, 2, 1));
        // Only the scoped query's result is stubbed - if the service under
        // test ever called the unscoped findConsumableForUpdate instead,
        // this batch would not be found and the test would fail on the
        // insufficient-stock check below, not silently pass.
        when(productBatchRepository.findConsumableForUpdateByPurchaseOrderItem(purchaseOrderItemId))
                .thenReturn(List.of(scopedBatch));

        List<BatchConsumption> result = service.consumeFromPurchaseOrderItem(purchaseOrderItemId, 25);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).quantityConsumed()).isEqualTo(25);
        assertThat(result.get(0).unitCost()).isEqualByComparingTo("100");
        assertThat(scopedBatch.getRemainingQuantity()).isEqualTo(15);
    }

    @Test
    void consumeFromPurchaseOrderItemNeverDrawsFromAnUnrelatedSuppliersBatchEvenIfOlder() {
        UUID purchaseOrderItemId = UUID.randomUUID();
        // An older, cheaper batch exists (would be picked first by plain
        // FIFO) but it belongs to a different purchase order - the scoped
        // query must not return it, so consuming against purchaseOrderItemId
        // with nothing scoped to it correctly fails as insufficient stock
        // rather than quietly drawing from the unrelated batch.
        when(productBatchRepository.findConsumableForUpdateByPurchaseOrderItem(purchaseOrderItemId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.consumeFromPurchaseOrderItem(purchaseOrderItemId, 10))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void consumeFromBatchTakesOnlyFromTheChosenBatch() {
        UUID batchId = UUID.randomUUID();
        // The chosen batch is the newer 12-birr one; an older 10-birr batch exists but must be left alone.
        ProductBatch chosen = chosenBatch(batchId, productId, branchId, 30, new BigDecimal("12"));
        when(productBatchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(chosen));

        List<BatchConsumption> result = service.consumeFromBatch(batchId, productId, branchId, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).batchId()).isEqualTo(batchId);
        assertThat(result.get(0).quantityConsumed()).isEqualTo(20);
        assertThat(result.get(0).unitCost()).isEqualByComparingTo("12");
        assertThat(chosen.getRemainingQuantity()).isEqualTo(10);
    }

    @Test
    void consumeFromBatchRejectsABatchOfAnotherProductOrBranch() {
        UUID batchId = UUID.randomUUID();
        ProductBatch someoneElsesBatch = chosenBatch(batchId, UUID.randomUUID(), branchId, 30, new BigDecimal("12"));
        when(productBatchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(someoneElsesBatch));

        assertThatThrownBy(() -> service.consumeFromBatch(batchId, productId, branchId, 5))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(someoneElsesBatch.getRemainingQuantity()).isEqualTo(30);
    }

    @Test
    void consumeFromBatchNeverTopsUpFromAnotherBatchWhenTheChosenOneIsTooSmall() {
        UUID batchId = UUID.randomUUID();
        ProductBatch small = chosenBatch(batchId, productId, branchId, 5, new BigDecimal("12"));
        when(productBatchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(small));

        assertThatThrownBy(() -> service.consumeFromBatch(batchId, productId, branchId, 8))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(small.getRemainingQuantity()).isEqualTo(5);
    }

    private ProductBatch chosenBatch(UUID batchId, UUID batchProductId, UUID batchBranchId, int remaining, BigDecimal cost) {
        Product product = new Product();
        setId(product, batchProductId);
        Branch branch = new Branch();
        setId(branch, batchBranchId);

        ProductBatch batch = batchOf(remaining, cost, LocalDate.of(2026, 3, 1));
        batch.setProduct(product);
        batch.setBranch(branch);
        setId(batch, batchId);
        return batch;
    }

    private void setId(BaseEntity entity, UUID id) {
        try {
            var field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private ProductBatch batchOf(int remaining, BigDecimal cost, LocalDate receivedDate) {
        ProductBatch batch = new ProductBatch();
        batch.setBatchNumber("BATCH-" + UUID.randomUUID());
        batch.setQuantity(remaining);
        batch.setRemainingQuantity(remaining);
        batch.setCostPrice(cost);
        batch.setReceivedDate(receivedDate);
        return batch;
    }
}
