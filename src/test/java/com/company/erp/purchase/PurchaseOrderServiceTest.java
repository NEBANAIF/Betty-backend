package com.company.erp.purchase;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.branch.BranchType;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.purchase.dto.CreatePurchaseOrderRequest;
import com.company.erp.purchase.dto.ReceivePurchaseItemRequest;
import com.company.erp.purchase.dto.ShipmentStageRequest;
import com.company.erp.purchase.dto.UpdateShipmentRequest;
import com.company.erp.purchase.dto.ReceivePurchaseRequest;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.UserPrincipal;
import com.company.erp.supplier.Supplier;
import com.company.erp.supplier.SupplierRepository;
import com.company.erp.user.Role;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private com.company.erp.debt.DebtService debtService;
    @Mock private SupplierRepository supplierRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchAccessService branchAccessService;
    @Mock private StockMutationService stockMutationService;
    @Mock private PurchaseReceiptRepository purchaseReceiptRepository;

    private PurchaseOrderService service;

    private final UUID branchId = UUID.randomUUID();
    private Branch branch;
    private Product product;
    private User adminUser;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderService(purchaseOrderRepository, supplierRepository, branchRepository,
                productRepository, userRepository, branchAccessService, stockMutationService, purchaseReceiptRepository, debtService);

        branch = new Branch();
        branch.setType(BranchType.WAREHOUSE);
        product = new Product();
        adminUser = new User();
        adminUser.setName("Admin");
        adminUser.setRole(Role.SUPER_ADMIN);

        UserPrincipal principal = new UserPrincipal(adminUser);
        lenient().when(branchAccessService.currentUser()).thenReturn(principal);
        lenient().when(userRepository.findById(any())).thenReturn(Optional.of(adminUser));
    }

    @Test
    void receivingMoreThanOutstandingIsRejected() {
        PurchaseOrder po = approvedPoWithOneItem(100, 40); // 40 already received, 60 remain

        when(purchaseOrderRepository.findById(po.getId())).thenReturn(Optional.of(po));

        var request = new ReceivePurchaseRequest(null,
                List.of(new ReceivePurchaseItemRequest(po.getItems().get(0).getId(), 61)));

        assertThatThrownBy(() -> service.receive(po.getId(), request))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void partialReceiptMovesStatusToPartiallyReceivedNotReceived() {
        PurchaseOrder po = approvedPoWithOneItem(100, 0);
        when(purchaseOrderRepository.findById(po.getId())).thenReturn(Optional.of(po));

        var request = new ReceivePurchaseRequest(null,
                List.of(new ReceivePurchaseItemRequest(po.getItems().get(0).getId(), 40)));

        var result = service.receive(po.getId(), request);

        assertThat(result.status()).isEqualTo("PARTIALLY_RECEIVED");
        verify(stockMutationService).receiveStock(
                any(), any(), org.mockito.ArgumentMatchers.eq(40), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void fullReceiptMovesStatusToReceived() {
        PurchaseOrder po = approvedPoWithOneItem(100, 40);
        when(purchaseOrderRepository.findById(po.getId())).thenReturn(Optional.of(po));

        var request = new ReceivePurchaseRequest(null,
                List.of(new ReceivePurchaseItemRequest(po.getItems().get(0).getId(), 60)));

        var result = service.receive(po.getId(), request);

        assertThat(result.status()).isEqualTo("RECEIVED");
    }

    @Test
    void cannotReceiveAPurchaseOrderThatIsNotApproved() {
        PurchaseOrder po = approvedPoWithOneItem(100, 0);
        po.setStatus(PurchaseOrderStatus.DRAFT);
        when(purchaseOrderRepository.findById(po.getId())).thenReturn(Optional.of(po));

        var request = new ReceivePurchaseRequest(null,
                List.of(new ReceivePurchaseItemRequest(po.getItems().get(0).getId(), 10)));

        assertThatThrownBy(() -> service.receive(po.getId(), request))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void aDeliveryAlreadyBookedInIsRejectedAndAddsNoStock() {
        PurchaseOrder po = approvedPoWithOneItem(100, 0);
        when(purchaseOrderRepository.findById(po.getId())).thenReturn(Optional.of(po));
        UUID requestId = UUID.randomUUID();
        when(purchaseReceiptRepository.existsByRequestId(requestId)).thenReturn(true);

        var request = new ReceivePurchaseRequest(null,
                List.of(new ReceivePurchaseItemRequest(po.getItems().get(0).getId(), 40)), requestId, null);

        assertThatThrownBy(() -> service.receive(po.getId(), request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already been received");
        verifyNoInteractions(stockMutationService);
        assertThat(po.getItems().get(0).getReceivedQuantity()).isZero();
    }

    @Test
    void receivingKeepsAReceiptOfWhoCountedWhat() {
        PurchaseOrder po = approvedPoWithOneItem(100, 0);
        when(purchaseOrderRepository.findById(po.getId())).thenReturn(Optional.of(po));

        var request = new ReceivePurchaseRequest(null,
                List.of(new ReceivePurchaseItemRequest(po.getItems().get(0).getId(), 40)), UUID.randomUUID(), "Truck 1 of 2");
        var result = service.receive(po.getId(), request);

        assertThat(po.getReceipts()).hasSize(1);
        assertThat(po.getReceipts().get(0).getLines()).hasSize(1);
        assertThat(po.getReceipts().get(0).getLines().get(0).getQuantity()).isEqualTo(40);
        assertThat(po.getReceipts().get(0).getNote()).isEqualTo("Truck 1 of 2");
        assertThat(result.receipts()).hasSize(1);
        assertThat(result.receivingStatus()).isEqualTo("PARTIALLY_RECEIVED");
    }

    @Test
    void importedStockIsCostedInTheLocalCurrency() {
        PurchaseOrder po = globalPo();
        po.setExchangeRate(new BigDecimal("130"));            // 1 USD = 130 local
        when(purchaseOrderRepository.findById(po.getId())).thenReturn(Optional.of(po));

        var request = new ReceivePurchaseRequest(null,
                List.of(new ReceivePurchaseItemRequest(po.getItems().get(0).getId(), 40)));
        service.receive(po.getId(), request);

        var cost = org.mockito.ArgumentCaptor.forClass(BigDecimal.class);
        verify(stockMutationService).receiveStock(any(), any(), org.mockito.ArgumentMatchers.eq(40), cost.capture(),
                any(), any(), any(), any(), any(), any(), any());
        assertThat(cost.getValue()).isEqualByComparingTo("14300");   // 110 USD x 130
    }

    @Test
    void localStockIsStillCostedExactlyAsOrdered() {
        PurchaseOrder po = approvedPoWithOneItem(100, 0);
        when(purchaseOrderRepository.findById(po.getId())).thenReturn(Optional.of(po));

        service.receive(po.getId(), new ReceivePurchaseRequest(null,
                List.of(new ReceivePurchaseItemRequest(po.getItems().get(0).getId(), 40))));

        var cost = org.mockito.ArgumentCaptor.forClass(BigDecimal.class);
        verify(stockMutationService).receiveStock(any(), any(), org.mockito.ArgumentMatchers.eq(40), cost.capture(),
                any(), any(), any(), any(), any(), any(), any());
        assertThat(cost.getValue()).isEqualByComparingTo("110");
    }

    @Test
    void updatingTheShipmentNeverAddsStockEvenWhenTheGoodsReachTheWarehouse() {
        PurchaseOrder po = globalPo();
        when(purchaseOrderRepository.findById(po.getId())).thenReturn(Optional.of(po));

        var route = List.of(
                new ShipmentStageRequest("China", null, null, LocalDate.now().minusDays(20), StageStatus.DEPARTED, null),
                new ShipmentStageRequest("Ethiopia", "Main Warehouse", LocalDate.now().minusDays(1), null,
                        StageStatus.ARRIVED, null));
        var result = service.updateShipment(po.getId(), new UpdateShipmentRequest(null, null, null, null, null, route));

        assertThat(result.shipmentStatus()).isEqualTo("ARRIVED");
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(po.getItems().get(0).getReceivedQuantity()).isZero();
        verifyNoInteractions(stockMutationService);
    }

    @Test
    void theRouteOfALocalPurchaseCannotBeUpdated() {
        PurchaseOrder po = approvedPoWithOneItem(10, 0);
        when(purchaseOrderRepository.findById(po.getId())).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> service.updateShipment(po.getId(), new UpdateShipmentRequest(null, null, null, null, null,
                List.of(new ShipmentStageRequest("China", null, null, null, null, null)))))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void creatingAGlobalPurchaseSavesTheRouteAndWorksOutItsShipmentStatus() {
        stubCreateDependencies();
        var request = globalRequest(branchId, List.of(
                new ShipmentStageRequest("China", "Shanghai", null, LocalDate.now().minusDays(5), StageStatus.DEPARTED, null),
                new ShipmentStageRequest("Ethiopia", "Main Warehouse", null, null, StageStatus.PENDING, null)));

        var result = service.create(request);

        assertThat(result.purchaseType()).isEqualTo("GLOBAL");
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.shipmentStatus()).isEqualTo("IN_TRANSIT");
        assertThat(result.route()).isEqualTo("Shanghai \u2192 Main Warehouse");
        assertThat(result.stages()).hasSize(2);
        assertThat(result.delayed()).isFalse();
    }

    @Test
    void aGlobalPurchaseMustBeReceivedIntoAWarehouse() {
        stubCreateDependencies();
        branch.setType(BranchType.STORE);
        var request = globalRequest(branchId, List.of(
                new ShipmentStageRequest("China", null, null, null, null, null),
                new ShipmentStageRequest("Ethiopia", null, null, null, null, null)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("warehouse");
    }

    @Test
    void aGlobalPurchaseNeedsItsShipmentDetails() {
        stubCreateDependencies();
        var noCurrency = new CreatePurchaseOrderRequest(UUID.randomUUID(), branchId, LocalDate.now(), oneItem(),
                PurchaseType.GLOBAL, "China", "Ethiopia", ShippingMethod.SEA, null, null, null,
                LocalDate.now().plusDays(30), List.of(
                        new ShipmentStageRequest("China", null, null, null, null, null),
                        new ShipmentStageRequest("Ethiopia", null, null, null, null, null)));
        assertThatThrownBy(() -> service.create(noCurrency)).hasMessageContaining("currency");

        var sameCountry = new CreatePurchaseOrderRequest(UUID.randomUUID(), branchId, LocalDate.now(), oneItem(),
                PurchaseType.GLOBAL, "Ethiopia", "ethiopia", ShippingMethod.ROAD, "ETB", null, null,
                LocalDate.now().plusDays(30), List.of());
        assertThatThrownBy(() -> service.create(sameCountry)).hasMessageContaining("same");
    }

    @Test
    void aLocalPurchaseIsCreatedExactlyAsBefore() {
        stubCreateDependencies();
        var result = service.create(new CreatePurchaseOrderRequest(UUID.randomUUID(), branchId, LocalDate.now(), oneItem()));

        assertThat(result.purchaseType()).isEqualTo("LOCAL");
        assertThat(result.shipmentStatus()).isNull();
        assertThat(result.stages()).isEmpty();
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.total()).isEqualByComparingTo("500");
    }

    private List<com.company.erp.purchase.dto.CreatePurchaseOrderItemRequest> oneItem() {
        return List.of(new com.company.erp.purchase.dto.CreatePurchaseOrderItemRequest(UUID.randomUUID(), 10, BigDecimal.valueOf(50)));
    }

    private CreatePurchaseOrderRequest globalRequest(UUID branchId, List<ShipmentStageRequest> stages) {
        return new CreatePurchaseOrderRequest(UUID.randomUUID(), branchId, LocalDate.now(), oneItem(),
                PurchaseType.GLOBAL, "China", "Ethiopia", ShippingMethod.SEA, "usd", new BigDecimal("130"),
                new BigDecimal("250"), LocalDate.now().plusDays(30), stages);
    }

    private void stubCreateDependencies() {
        Supplier supplier = new Supplier();
        lenient().when(supplierRepository.findById(any())).thenReturn(Optional.of(supplier));
        lenient().when(branchRepository.findById(any())).thenReturn(Optional.of(branch));
        lenient().when(productRepository.findById(any())).thenReturn(Optional.of(product));
        lenient().when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private PurchaseOrder globalPo() {
        PurchaseOrder po = approvedPoWithOneItem(100, 0);
        po.setPurchaseType(PurchaseType.GLOBAL);
        po.setOriginCountry("China");
        po.setDestinationCountry("Ethiopia");
        po.setCurrency("USD");
        po.setOrderDate(LocalDate.now().minusDays(30));
        po.setExpectedArrivalDate(LocalDate.now().plusDays(10));
        for (String[] s : new String[][]{{"China", "1"}, {"Ethiopia", "2"}}) {
            PurchaseShipmentStage stage = new PurchaseShipmentStage();
            stage.setStageOrder(Integer.parseInt(s[1]));
            stage.setCountry(s[0]);
            stage.setStatus(StageStatus.PENDING);
            po.addStage(stage);
        }
        return po;
    }

    private PurchaseOrder approvedPoWithOneItem(int quantity, int alreadyReceived) {
        PurchaseOrder po = new PurchaseOrder();
        setId(po, UUID.randomUUID());
        po.setBranch(branch);
        po.setSupplier(new Supplier());
        po.setStatus(alreadyReceived > 0 ? PurchaseOrderStatus.PARTIALLY_RECEIVED : PurchaseOrderStatus.APPROVED);
        po.setTotal(BigDecimal.valueOf(1000));
        po.setCreatedBy(adminUser);

        PurchaseOrderItem item = new PurchaseOrderItem();
        setId(item, UUID.randomUUID());
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setReceivedQuantity(alreadyReceived);
        item.setUnitCost(BigDecimal.valueOf(110));
        po.addItem(item);

        return po;
    }

    private void setId(com.company.erp.common.audit.BaseEntity entity, UUID id) {
        try {
            var field = com.company.erp.common.audit.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
