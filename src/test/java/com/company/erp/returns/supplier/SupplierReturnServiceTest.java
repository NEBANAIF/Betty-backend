package com.company.erp.returns.supplier;

import com.company.erp.batch.BatchConsumption;
import com.company.erp.batch.ProductBatch;
import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.product.Product;
import com.company.erp.purchase.PurchaseOrder;
import com.company.erp.purchase.PurchaseOrderItem;
import com.company.erp.purchase.PurchaseOrderRepository;
import com.company.erp.purchase.PurchaseOrderStatus;
import com.company.erp.returns.supplier.dto.CreateSupplierReturnItemRequest;
import com.company.erp.returns.supplier.dto.CreateSupplierReturnRequest;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.UserPrincipal;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.supplier.Supplier;
import com.company.erp.user.Role;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierReturnServiceTest {

    @Mock private SupplierReturnRepository supplierReturnRepository;
    @Mock private SupplierReturnItemRepository supplierReturnItemRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchAccessService branchAccessService;
    @Mock private StockMutationService stockMutationService;
    @Mock private ProductBatchRepository productBatchRepository;

    private SupplierReturnService service;

    private Branch mainWarehouse;
    private Supplier supplier;
    private Product product;
    private ProductBatch batch;
    private PurchaseOrder purchaseOrder;
    private PurchaseOrderItem purchaseOrderItem;
    private UUID purchaseOrderId;
    private User manager;

    @BeforeEach
    void setUp() {
        service = new SupplierReturnService(supplierReturnRepository, supplierReturnItemRepository,
                purchaseOrderRepository, userRepository, branchAccessService, stockMutationService,
                productBatchRepository);

        mainWarehouse = new Branch();
        mainWarehouse.setName("Main Warehouse");
        setId(mainWarehouse, UUID.randomUUID());

        supplier = new Supplier();
        supplier.setName("Acme Cables");
        setId(supplier, UUID.randomUUID());

        product = new Product();
        product.setName("Cable 2.5mm");
        setId(product, UUID.randomUUID());

        batch = new ProductBatch();
        batch.setBatchNumber("BATCH-MAIN-0001");
        batch.setCostPrice(BigDecimal.valueOf(110));
        setId(batch, UUID.randomUUID());

        manager = new User();
        manager.setName("Main Warehouse Manager");
        manager.setRole(Role.WAREHOUSE_MANAGER);
        setId(manager, UUID.randomUUID());

        purchaseOrderId = UUID.randomUUID();
        purchaseOrder = new PurchaseOrder();
        setId(purchaseOrder, purchaseOrderId);
        purchaseOrder.setOrderNumber("PO-20260101-0001");
        purchaseOrder.setSupplier(supplier);
        purchaseOrder.setBranch(mainWarehouse);
        purchaseOrder.setStatus(PurchaseOrderStatus.RECEIVED);

        purchaseOrderItem = new PurchaseOrderItem();
        purchaseOrderItem.setProduct(product);
        purchaseOrderItem.setQuantity(500);
        purchaseOrderItem.setReceivedQuantity(500);
        purchaseOrderItem.setUnitCost(BigDecimal.valueOf(110));
        setId(purchaseOrderItem, UUID.randomUUID());
        purchaseOrder.addItem(purchaseOrderItem);

        UserPrincipal principal = new UserPrincipal(manager);
        lenient().when(branchAccessService.currentUser()).thenReturn(principal);
        lenient().when(userRepository.findById(any())).thenReturn(Optional.of(manager));
        lenient().when(purchaseOrderRepository.findById(purchaseOrderId)).thenReturn(Optional.of(purchaseOrder));
        lenient().when(supplierReturnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(supplierReturnItemRepository.sumReturnedForPurchaseOrderItem(any())).thenReturn(0);
        lenient().when(productBatchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
    }

    @Test
    void createsAllocationsFromWhateverFifoActuallyConsumedAndPreservesCost() {
        when(stockMutationService.issueFromPurchaseOrderItem(eq(product), eq(mainWarehouse),
                eq(purchaseOrderItem.getId()), eq(30), eq(StockMovementType.SUPPLIER_RETURN), any(), eq(manager),
                any(), eq(purchaseOrderId)))
                .thenReturn(List.of(new BatchConsumption(batch.getId(), batch.getBatchNumber(), 30, BigDecimal.valueOf(110))));

        var request = new CreateSupplierReturnRequest(purchaseOrderId,
                List.of(new CreateSupplierReturnItemRequest(purchaseOrderItem.getId(), 30)));

        var result = service.create(request);

        assertThat(result.supplierId()).isEqualTo(supplier.getId());
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).allocations()).hasSize(1);
        assertThat(result.items().get(0).allocations().get(0).unitCost()).isEqualByComparingTo("110");
        assertThat(result.totalValue()).isEqualByComparingTo("3300"); // 30 * 110
    }

    @Test
    void cannotReturnMoreThanWasReceivedOnThatLine() {
        var request = new CreateSupplierReturnRequest(purchaseOrderId,
                List.of(new CreateSupplierReturnItemRequest(purchaseOrderItem.getId(), 501)));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void secondReturnIsCappedByWhatsAlreadyReturnedOnTheSameLine() {
        when(supplierReturnItemRepository.sumReturnedForPurchaseOrderItem(purchaseOrderItem.getId())).thenReturn(480);

        var request = new CreateSupplierReturnRequest(purchaseOrderId,
                List.of(new CreateSupplierReturnItemRequest(purchaseOrderItem.getId(), 30)));

        // 480 already returned + 30 more > 500 received.
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void aPendingPurchaseOrderHasNothingToReturn() {
        purchaseOrder.setStatus(PurchaseOrderStatus.PENDING);

        var request = new CreateSupplierReturnRequest(purchaseOrderId,
                List.of(new CreateSupplierReturnItemRequest(purchaseOrderItem.getId(), 10)));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void anItemFromAnotherPurchaseOrderIsRejected() {
        UUID foreignItemId = UUID.randomUUID();
        var request = new CreateSupplierReturnRequest(purchaseOrderId,
                List.of(new CreateSupplierReturnItemRequest(foreignItemId, 1)));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(ForbiddenException.class);
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
