package com.company.erp.inventory;

import com.company.erp.batch.BatchConsumption;
import com.company.erp.batch.BatchService;
import com.company.erp.batch.FifoConsumptionService;
import com.company.erp.batch.ProductBatch;
import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.notification.NotificationService;
import com.company.erp.product.Product;
import com.company.erp.stockhistory.StockHistoryService;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.stockhistory.StockReferenceType;
import com.company.erp.user.User;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StockMutationService is documented as THE sole entry point for every
 * stock mutation in the codebase - every other test (PosCheckoutServiceTest,
 * PurchaseOrderServiceTest, StockTransferServiceTest, CustomerReturnServiceTest,
 * SupplierReturnServiceTest) mocks it rather than exercising it, so this is
 * the only place its own internal correctness - the Inventory-aggregate
 * math, and the per-batch StockHistory reconciliation loop in particular -
 * is actually verified rather than assumed.
 */
@ExtendWith(MockitoExtension.class)
class StockMutationServiceTest {

    @Mock private InventoryService inventoryService;
    @Mock private BatchService batchService;
    @Mock private FifoConsumptionService fifoConsumptionService;
    @Mock private ProductBatchRepository productBatchRepository;
    @Mock private StockHistoryService stockHistoryService;
    @Mock private NotificationService notificationService;

    private StockMutationService service;

    private Product product;
    private Branch branch;
    private User user;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        service = new StockMutationService(inventoryService, batchService, fifoConsumptionService,
                productBatchRepository, stockHistoryService, notificationService);

        product = new Product();
        product.setName("Cable 2.5mm");
        branch = new Branch();
        branch.setName("Main Warehouse");
        user = new User();
        user.setName("Warehouse Manager");
        inventory = new Inventory();
        inventory.setQuantity(0);

        lenient().when(inventoryService.getOrCreateForUpdate(product, branch)).thenReturn(inventory);
    }

    @Test
    void receiveStockCreatesABatchIncreasesInventoryAndRecordsHistory() {
        ProductBatch batch = batchWithId(UUID.randomUUID(), "BATCH-0001");
        when(batchService.createBatch(product, branch, 500, BigDecimal.valueOf(110),
                LocalDate.of(2026, 1, 1), null)).thenReturn(batch);
        when(inventoryService.increase(inventory, 500)).thenAnswer(inv -> {
            int previous = inventory.getQuantity();
            inventory.setQuantity(previous + 500);
            return previous;
        });

        ProductBatch result = service.receiveStock(product, branch, 500, BigDecimal.valueOf(110),
                LocalDate.of(2026, 1, 1), StockMovementType.PURCHASE, "PO-1", user,
                StockReferenceType.PURCHASE_ORDER, UUID.randomUUID());

        assertThat(result).isSameAs(batch);
        assertThat(inventory.getQuantity()).isEqualTo(500);
        verify(stockHistoryService).record(eq(product), eq(branch), eq(batch), eq(StockMovementType.PURCHASE),
                eq(500), eq(0), eq(500), eq("PO-1"), eq(user), eq(StockReferenceType.PURCHASE_ORDER), any());
        // and one notification for the activity feed, with the quantity signed (+ = stock in)
        verify(notificationService).recordStockChange(product, branch, 500, StockMovementType.PURCHASE, "PO-1", user);
    }

    @Test
    void receiveStockOverloadThreadsThePurchaseOrderItemIdIntoBatchCreation() {
        UUID poItemId = UUID.randomUUID();
        ProductBatch batch = batchWithId(UUID.randomUUID(), "BATCH-0002");
        when(batchService.createBatch(product, branch, 100, BigDecimal.TEN, LocalDate.now(), poItemId))
                .thenReturn(batch);
        when(inventoryService.increase(inventory, 100)).thenReturn(0);

        service.receiveStock(product, branch, 100, BigDecimal.TEN, LocalDate.now(), StockMovementType.PURCHASE,
                "PO-2", user, StockReferenceType.PURCHASE_ORDER, UUID.randomUUID(), poItemId);

        verify(batchService).createBatch(product, branch, 100, BigDecimal.TEN, LocalDate.now(), poItemId);
    }

    @Test
    void receiveStockRejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> service.receiveStock(product, branch, 0, BigDecimal.TEN, LocalDate.now(),
                StockMovementType.PURCHASE, "x", user, StockReferenceType.PURCHASE_ORDER, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void issueStockConsumesFifoDecreasesInventoryOnceAndWritesOneHistoryRowPerBatch() {
        inventory.setQuantity(150);
        UUID batchAId = UUID.randomUUID();
        UUID batchBId = UUID.randomUUID();
        when(fifoConsumptionService.consume(product.getId(), branch.getId(), 150)).thenReturn(List.of(
                new BatchConsumption(batchAId, "BATCH-A", 100, BigDecimal.valueOf(110)),
                new BatchConsumption(batchBId, "BATCH-B", 50, BigDecimal.valueOf(120))));
        when(inventoryService.decrease(inventory, 150)).thenAnswer(inv -> {
            inventory.setQuantity(0);
            return 150; // previous total before the decrease
        });
        when(productBatchRepository.findById(batchAId)).thenReturn(Optional.of(batchWithId(batchAId, "BATCH-A")));
        when(productBatchRepository.findById(batchBId)).thenReturn(Optional.of(batchWithId(batchBId, "BATCH-B")));

        List<BatchConsumption> result = service.issueStock(product, branch, 150, StockMovementType.SALE,
                "Sale", user, StockReferenceType.SALE, UUID.randomUUID());

        assertThat(result).hasSize(2);
        verify(inventoryService, times(1)).decrease(inventory, 150);
        // Batch A: 150 -> 50. Batch B: 50 -> 0. Each recorded as its own negative movement.
        verify(stockHistoryService).record(eq(product), eq(branch), any(), eq(StockMovementType.SALE),
                eq(-100), eq(150), eq(50), any(), eq(user), any(), any());
        verify(stockHistoryService).record(eq(product), eq(branch), any(), eq(StockMovementType.SALE),
                eq(-50), eq(50), eq(0), any(), eq(user), any(), any());
    }

    @Test
    void issueStockRejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> service.issueStock(product, branch, -1, StockMovementType.SALE, "x", user,
                StockReferenceType.SALE, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void issueFromPurchaseOrderItemUsesTheScopedConsumptionNotGenericFifo() {
        UUID poItemId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        inventory.setQuantity(50);
        when(fifoConsumptionService.consumeFromPurchaseOrderItem(poItemId, 20)).thenReturn(
                List.of(new BatchConsumption(batchId, "BATCH-X", 20, BigDecimal.valueOf(110))));
        when(inventoryService.decrease(inventory, 20)).thenReturn(50);
        when(productBatchRepository.findById(batchId)).thenReturn(Optional.of(batchWithId(batchId, "BATCH-X")));

        service.issueFromPurchaseOrderItem(product, branch, poItemId, 20, StockMovementType.SUPPLIER_RETURN,
                "Return to supplier", user, StockReferenceType.SUPPLIER_RETURN, UUID.randomUUID());

        verify(fifoConsumptionService).consumeFromPurchaseOrderItem(poItemId, 20);
        verify(fifoConsumptionService, never()).consume(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void restockToBatchRestoresTheExactBatchAndIncreasesInventory() {
        UUID batchId = UUID.randomUUID();
        ProductBatch batch = batchWithId(batchId, "BATCH-ORIG");
        when(productBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(inventoryService.increase(inventory, 5)).thenAnswer(inv -> {
            int previous = inventory.getQuantity();
            inventory.setQuantity(previous + 5);
            return previous;
        });

        service.restockToBatch(product, branch, batchId, 5, StockMovementType.CUSTOMER_RETURN,
                "Return", user, StockReferenceType.CUSTOMER_RETURN, UUID.randomUUID());

        verify(fifoConsumptionService).restoreToBatch(batchId, 5);
        verify(stockHistoryService).record(eq(product), eq(branch), eq(batch), eq(StockMovementType.CUSTOMER_RETURN),
                eq(5), eq(0), eq(5), eq("Return"), eq(user), eq(StockReferenceType.CUSTOMER_RETURN), any());
    }

    @Test
    void restockToBatchRejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> service.restockToBatch(product, branch, UUID.randomUUID(), 0,
                StockMovementType.CUSTOMER_RETURN, "x", user, StockReferenceType.CUSTOMER_RETURN, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void issueStockTripsTheReconciliationGuardIfPerBatchMathEverDriftsFromTheAggregate() {
        // Contrived: decrease() reports a previous total inconsistent with
        // what the consumption list actually sums to, so the running
        // before/after math in the loop can never land on the real new
        // total - this is the safety net for a bug in that loop, not a
        // realistic production scenario.
        inventory.setQuantity(999); // deliberately wrong "new" total
        UUID batchId = UUID.randomUUID();
        when(fifoConsumptionService.consume(product.getId(), branch.getId(), 10))
                .thenReturn(List.of(new BatchConsumption(batchId, "BATCH-A", 10, BigDecimal.TEN)));
        when(inventoryService.decrease(inventory, 10)).thenReturn(10);
        when(productBatchRepository.findById(batchId)).thenReturn(Optional.of(batchWithId(batchId, "BATCH-A")));

        assertThatThrownBy(() -> service.issueStock(product, branch, 10, StockMovementType.SALE, "x", user,
                StockReferenceType.SALE, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reconciliation");
    }

    private ProductBatch batchWithId(UUID id, String batchNumber) {
        ProductBatch batch = new ProductBatch();
        batch.setBatchNumber(batchNumber);
        setId(batch, id);
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
}
