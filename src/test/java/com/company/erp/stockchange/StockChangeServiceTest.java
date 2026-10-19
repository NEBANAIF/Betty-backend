package com.company.erp.stockchange;

import com.company.erp.batch.ProductBatch;
import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.branch.BranchType;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.inventory.Inventory;
import com.company.erp.inventory.InventoryRepository;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.notification.NotificationService;
import com.company.erp.notification.ReviewStatus;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.ContentAccess;
import com.company.erp.security.UserPrincipal;
import com.company.erp.stockchange.dto.SubmitStockChangeRequest;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.stockhistory.StockReferenceType;
import com.company.erp.user.Permission;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockChangeServiceTest {

    @Mock private StockChangeRequestRepository requestRepository;
    @Mock private ProductRepository productRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private ProductBatchRepository productBatchRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchAccessService branchAccessService;
    @Mock private StockMutationService stockMutationService;
    @Mock private NotificationService notificationService;
    @Mock private ContentAccess contentAccess;

    private StockChangeService service;

    private Product product;
    private Branch branch;
    private User manager;

    @BeforeEach
    void setUp() {
        service = new StockChangeService(requestRepository, productRepository, branchRepository,
                productBatchRepository, inventoryRepository, userRepository, branchAccessService,
                stockMutationService, notificationService, contentAccess);

        product = new Product();
        product.setName("Cable 2.5mm");
        product.setUnit("pcs");
        setId(product, UUID.randomUUID());
        branch = new Branch();
        branch.setName("Bole Store");
        branch.setType(BranchType.STORE);
        setId(branch, UUID.randomUUID());
        manager = new User();
        manager.setName("Store Manager");
        manager.setRole(Role.SUPER_ADMIN); // holds every permission
        setId(manager, UUID.randomUUID());

        lenient().when(branchAccessService.currentUser()).thenReturn(new UserPrincipal(manager));
        lenient().when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        lenient().when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        lenient().when(branchRepository.findById(branch.getId())).thenReturn(Optional.of(branch));
        lenient().when(requestRepository.save(any(StockChangeRequest.class))).thenAnswer(inv -> {
            StockChangeRequest r = inv.getArgument(0);
            setId(r, UUID.randomUUID());
            return r;
        });
        lenient().when(contentAccess.can(Permission.VIEW_COSTS)).thenReturn(true);
    }

    @Test
    void enteringABatchOnlyHoldsItNothingMovesUntilItIsApproved() {
        var response = service.submit(new SubmitStockChangeRequest(StockChangeType.ADD_BATCH, product.getId(),
                branch.getId(), 5, new BigDecimal("12.50"), null, LocalDate.of(2026, 9, 1), null, null, null, "Initial stock"));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.quantity()).isEqualTo(5);
        assertThat(response.costPrice()).isEqualByComparingTo("12.50");
        verify(branchAccessService).assertCanWriteToBranch(branch.getId());
        verifyNoInteractions(stockMutationService); // the stock has NOT moved
        verify(notificationService).record(any(), eq("Stock change waiting for approval"),
                eq("Add 5 pcs of Cable 2.5mm at Bole Store — Initial stock"), eq(branch), eq(manager),
                eq(NotificationService.STOCK_CHANGE_REQUEST), any());
    }

    @Test
    void approvingMovesTheStockAsEnteredAndRecordsWhoApprovedIt() {
        var held = service.submit(new SubmitStockChangeRequest(StockChangeType.ADD_BATCH, product.getId(),
                branch.getId(), 50, new BigDecimal("12.50"), null, LocalDate.of(2026, 9, 1), null, null, null, "Initial stock"));
        StockChangeRequest stored = captureSaved();
        when(requestRepository.findById(held.id())).thenReturn(Optional.of(stored));

        var approved = service.approve(held.id());

        assertThat(approved.status()).isEqualTo("APPROVED");
        verify(stockMutationService).receiveStock(eq(product), eq(branch), eq(50), eq(new BigDecimal("12.50")),
                eq(LocalDate.of(2026, 9, 1)), eq(StockMovementType.BATCH_ADDED),
                eq("Initial stock (approved by Store Manager)"), eq(manager),
                eq(StockReferenceType.MANUAL_ADJUSTMENT), eq(held.id()));
        verify(notificationService).resolveStockChangeRequest(held.id(), ReviewStatus.APPROVED, manager);
    }

    @Test
    void aRejectedChangeNeverMovesStockAndCannotBeApprovedAfterwards() {
        var held = service.submit(new SubmitStockChangeRequest(StockChangeType.ADD_BATCH, product.getId(),
                branch.getId(), 5, BigDecimal.TEN, null, null, null, null, null, null));
        StockChangeRequest stored = captureSaved();
        when(requestRepository.findById(held.id())).thenReturn(Optional.of(stored));

        var rejected = service.reject(held.id(), "typed 5, meant 50");

        assertThat(rejected.status()).isEqualTo("REJECTED");
        verify(notificationService).resolveStockChangeRequest(held.id(), ReviewStatus.REJECTED, manager);
        assertThatThrownBy(() -> service.approve(held.id())).isInstanceOf(BusinessRuleViolationException.class);
        verify(stockMutationService, never()).receiveStock(any(), any(), anyInt(), any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void removingStockNeedsAReasonAndEnoughStockToTakeOut() {
        Inventory inventory = new Inventory();
        inventory.setQuantity(10);
        when(inventoryRepository.findByProductIdAndBranchId(product.getId(), branch.getId()))
                .thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> service.submit(new SubmitStockChangeRequest(StockChangeType.REMOVE_STOCK,
                product.getId(), branch.getId(), 3, null, null, null, StockMovementType.DAMAGED, null, null, "  ")))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("reason");

        assertThatThrownBy(() -> service.submit(new SubmitStockChangeRequest(StockChangeType.REMOVE_STOCK,
                product.getId(), branch.getId(), 11, null, null, null, StockMovementType.DAMAGED, null, null, "broken")))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("Only 10");

        assertThatThrownBy(() -> service.submit(new SubmitStockChangeRequest(StockChangeType.REMOVE_STOCK,
                product.getId(), branch.getId(), 3, null, null, null, StockMovementType.SALE, null, null, "x")))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("why");
    }

    @Test
    void approvingARemovalTakesTheStockOutOldestFirstWithTheChosenReason() {
        Inventory inventory = new Inventory();
        inventory.setQuantity(100);
        when(inventoryRepository.findByProductIdAndBranchId(product.getId(), branch.getId()))
                .thenReturn(Optional.of(inventory));
        var held = service.submit(new SubmitStockChangeRequest(StockChangeType.REMOVE_STOCK, product.getId(),
                branch.getId(), 45, null, null, null, StockMovementType.ADJUSTMENT, null, null, "Initial stock was entered as 500, should be 455"));
        StockChangeRequest stored = captureSaved();
        when(requestRepository.findById(held.id())).thenReturn(Optional.of(stored));

        service.approve(held.id());

        verify(stockMutationService).issueStock(eq(product), eq(branch), eq(45), eq(StockMovementType.ADJUSTMENT),
                eq("Initial stock was entered as 500, should be 455 (approved by Store Manager)"), eq(manager),
                eq(StockReferenceType.MANUAL_ADJUSTMENT), eq(held.id()));
    }

    @Test
    void aBatchThatBelongsToAnotherProductIsRefused() {
        ProductBatch other = new ProductBatch();
        Product otherProduct = new Product();
        setId(otherProduct, UUID.randomUUID());
        other.setProduct(otherProduct);
        other.setBranch(branch);
        other.setRemainingQuantity(50);
        UUID batchId = UUID.randomUUID();
        when(productBatchRepository.findById(batchId)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.submit(new SubmitStockChangeRequest(StockChangeType.REMOVE_STOCK,
                product.getId(), branch.getId(), 3, null, null, null, StockMovementType.LOST, batchId, null, "gone")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void someoneWithoutTheRightPermissionCannotEnterOrDiscardOthersChanges() {
        User staff = new User();
        staff.setName("Store Staff");
        staff.setRole(Role.STORE_STAFF); // no BATCH_CREATE, no STOCK_ADJUST, no STOCK_APPROVE
        setId(staff, UUID.randomUUID());
        when(branchAccessService.currentUser()).thenReturn(new UserPrincipal(staff));

        assertThatThrownBy(() -> service.submit(new SubmitStockChangeRequest(StockChangeType.ADD_BATCH,
                product.getId(), branch.getId(), 5, BigDecimal.ONE, null, null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);

        StockChangeRequest others = new StockChangeRequest();
        setId(others, UUID.randomUUID());
        others.setBranch(branch);
        others.setProduct(product);
        others.setRequestedBy(manager); // entered by someone else
        when(requestRepository.findById(others.getId())).thenReturn(Optional.of(others));

        assertThatThrownBy(() -> service.reject(others.getId(), null)).isInstanceOf(ForbiddenException.class);
        assertThat(others.getStatus()).isEqualTo(StockChangeStatus.PENDING);
    }

    private StockChangeRequest captureSaved() {
        org.mockito.ArgumentCaptor<StockChangeRequest> saved = org.mockito.ArgumentCaptor.forClass(StockChangeRequest.class);
        verify(requestRepository).save(saved.capture());
        return saved.getValue();
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
