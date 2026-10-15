package com.company.erp.transfer;

import com.company.erp.batch.BatchConsumption;
import com.company.erp.batch.ProductBatch;
import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.branch.BranchType;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.UserPrincipal;
import com.company.erp.transfer.dto.CreateTransferItemRequest;
import com.company.erp.transfer.dto.CreateTransferRequest;
import com.company.erp.user.Role;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockTransferServiceTest {

    @Mock private StockTransferRepository stockTransferRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchAccessService branchAccessService;
    @Mock private StockMutationService stockMutationService;
    @Mock private ProductBatchRepository productBatchRepository;

    private StockTransferService service;

    private Branch mainWarehouse;
    private Branch boleStore;
    private Product product;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new StockTransferService(stockTransferRepository, branchRepository, productRepository,
                userRepository, branchAccessService, stockMutationService, productBatchRepository);

        mainWarehouse = new Branch();
        mainWarehouse.setName("Main Warehouse");
        mainWarehouse.setType(BranchType.WAREHOUSE);
        setId(mainWarehouse, UUID.randomUUID());

        boleStore = new Branch();
        boleStore.setName("Bole Store");
        boleStore.setType(BranchType.STORE);
        setId(boleStore, UUID.randomUUID());

        product = new Product();
        product.setName("Cable 2.5mm");

        admin = new User();
        admin.setName("Super Admin");
        admin.setRole(Role.SUPER_ADMIN);

        UserPrincipal principal = new UserPrincipal(admin);
        lenient().when(branchAccessService.currentUser()).thenReturn(principal);
        lenient().when(userRepository.findById(any())).thenReturn(Optional.of(admin));
        lenient().when(branchRepository.findById(mainWarehouse.getId())).thenReturn(Optional.of(mainWarehouse));
        lenient().when(branchRepository.findById(boleStore.getId())).thenReturn(Optional.of(boleStore));
        lenient().when(productRepository.findById(any())).thenReturn(Optional.of(product));
        lenient().when(stockTransferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(stockTransferRepository.existsByTransferNumber(any())).thenReturn(false);
    }

    @Test
    void dispatchPreservesBatchCostAndReceiveCreatesDestinationBatchAtThatCost() {
        StockTransfer transfer = pendingTransfer(100);
        transfer.setStatus(TransferStatus.APPROVED);
        when(stockTransferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));

        UUID sourceBatchId = UUID.randomUUID();
        when(stockMutationService.issueStock(any(), any(), org.mockito.ArgumentMatchers.eq(100), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new BatchConsumption(sourceBatchId, "BATCH-MAIN-0001", 100, BigDecimal.valueOf(110))));
        ProductBatch sourceBatch = new ProductBatch();
        sourceBatch.setBatchNumber("BATCH-MAIN-0001");
        when(productBatchRepository.getReferenceById(sourceBatchId)).thenReturn(sourceBatch);

        var dispatched = service.dispatch(transfer.getId());
        assertThat(dispatched.status()).isEqualTo("IN_TRANSIT");
        assertThat(transfer.getItems().get(0).getAllocations()).hasSize(1);
        assertThat(transfer.getItems().get(0).getAllocations().get(0).getUnitCost())
                .isEqualByComparingTo("110");

        // Now receive: the destination batch must be created at cost 110, not recalculated.
        var received = service.receive(transfer.getId());
        assertThat(received.status()).isEqualTo("RECEIVED");

        ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(stockMutationService).receiveStock(
                any(), org.mockito.ArgumentMatchers.eq(boleStore), org.mockito.ArgumentMatchers.eq(100),
                costCaptor.capture(), any(LocalDate.class), any(), any(), any(), any(), any());
        assertThat(costCaptor.getValue()).isEqualByComparingTo("110");
    }

    @Test
    void cannotDispatchATransferThatIsNotApproved() {
        StockTransfer transfer = pendingTransfer(50); // still PENDING
        when(stockTransferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.dispatch(transfer.getId()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void cannotReceiveATransferThatHasNotBeenDispatched() {
        StockTransfer transfer = pendingTransfer(50);
        transfer.setStatus(TransferStatus.APPROVED); // approved, but not yet IN_TRANSIT
        when(stockTransferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.receive(transfer.getId()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void cannotRejectATransferThatIsAlreadyInTransit() {
        StockTransfer transfer = pendingTransfer(50);
        transfer.setStatus(TransferStatus.IN_TRANSIT);
        when(stockTransferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.reject(transfer.getId(),
                new com.company.erp.transfer.dto.RejectTransferRequest("changed our mind")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void sourceAndDestinationBranchMustDiffer() {
        var request = new CreateTransferRequest(mainWarehouse.getId(), mainWarehouse.getId(),
                List.of(new CreateTransferItemRequest(UUID.randomUUID(), 10)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    private StockTransfer pendingTransfer(int quantity) {
        StockTransfer transfer = new StockTransfer();
        setId(transfer, UUID.randomUUID());
        transfer.setTransferNumber("TRF-20260115-1234");
        transfer.setSourceBranch(mainWarehouse);
        transfer.setDestinationBranch(boleStore);
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setRequestedBy(admin);

        StockTransferItem item = new StockTransferItem();
        setId(item, UUID.randomUUID());
        item.setProduct(product);
        item.setQuantity(quantity);
        transfer.addItem(item);

        return transfer;
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
