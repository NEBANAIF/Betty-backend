package com.company.erp.inventory;

import com.company.erp.batch.ProductBatch;
import com.company.erp.batch.dto.CreateBatchRequest;
import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.branch.BranchType;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.ContentAccess;
import com.company.erp.security.UserPrincipal;
import com.company.erp.stockhistory.StockMovementType;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchEntryServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchAccessService branchAccessService;
    @Mock private StockMutationService stockMutationService;
    @Mock private ContentAccess contentAccess;

    private BatchEntryService service;

    private Product product;
    private Branch branch;
    private User user;

    @BeforeEach
    void setUp() {
        service = new BatchEntryService(productRepository, branchRepository, userRepository,
                branchAccessService, stockMutationService, contentAccess);

        product = new Product();
        product.setName("Cable 2.5mm");
        setId(product, UUID.randomUUID());

        branch = new Branch();
        branch.setName("Bole Store");
        branch.setType(BranchType.STORE);
        setId(branch, UUID.randomUUID());

        user = new User();
        user.setName("Store Manager");
        user.setRole(Role.SUPER_ADMIN);

        lenient().when(branchAccessService.currentUser()).thenReturn(new UserPrincipal(user));
        lenient().when(userRepository.findById(any())).thenReturn(Optional.of(user));
        lenient().when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        lenient().when(branchRepository.findById(branch.getId())).thenReturn(Optional.of(branch));
        lenient().when(contentAccess.can(Permission.VIEW_COSTS)).thenReturn(true);
        lenient().when(stockMutationService.receiveStock(any(), any(), anyInt(), any(), any(), any(), any(), any(),
                any(), any())).thenAnswer(inv -> {
            ProductBatch batch = new ProductBatch();
            batch.setBatchNumber("BATCH-BOLE-20260923-1234");
            batch.setProduct(inv.getArgument(0));
            batch.setBranch(inv.getArgument(1));
            batch.setQuantity(inv.getArgument(2));
            batch.setRemainingQuantity(inv.getArgument(2));
            batch.setCostPrice(inv.getArgument(3));
            batch.setReceivedDate(inv.getArgument(4));
            setId(batch, UUID.randomUUID());
            return batch;
        });
    }

    @Test
    void addsTheBatchThroughTheStockMutationServiceAsBatchAddedWithNoReference() {
        var request = new CreateBatchRequest(product.getId(), branch.getId(), 25, new BigDecimal("12.50"),
                LocalDate.of(2026, 9, 1), "Imported by the store");

        var response = service.create(request);

        verify(branchAccessService).assertCanWriteToBranch(branch.getId());
        verify(stockMutationService).receiveStock(eq(product), eq(branch), eq(25), eq(new BigDecimal("12.50")),
                eq(LocalDate.of(2026, 9, 1)), eq(StockMovementType.BATCH_ADDED), eq("Imported by the store"),
                eq(user), isNull(), isNull());
        assertThat(response.quantity()).isEqualTo(25);
        assertThat(response.remainingQuantity()).isEqualTo(25);
        assertThat(response.costPrice()).isEqualByComparingTo("12.50");
        assertThat(response.batchNumber()).isEqualTo("BATCH-BOLE-20260923-1234");
    }

    @Test
    void aBlankNoteGetsADefaultReasonAndAMissingDateDefaultsToToday() {
        var request = new CreateBatchRequest(product.getId(), branch.getId(), 5, BigDecimal.ONE, null, "   ");

        service.create(request);

        verify(stockMutationService).receiveStock(eq(product), eq(branch), eq(5), eq(BigDecimal.ONE),
                any(LocalDate.class), eq(StockMovementType.BATCH_ADDED), eq("Batch added manually"),
                eq(user), isNull(), isNull());
    }

    @Test
    void leavesTheCostOutOfTheResponseForARoleWithoutTheCostPermission() {
        when(contentAccess.can(Permission.VIEW_COSTS)).thenReturn(false);
        var request = new CreateBatchRequest(product.getId(), branch.getId(), 5, BigDecimal.TEN, null, null);

        var response = service.create(request);

        assertThat(response.costPrice()).isNull();
    }

    @Test
    void refusesABranchTheUserCannotWriteToAndTouchesNoStock() {
        doThrow(ForbiddenException.branchAccessDenied(branch.getId()))
                .when(branchAccessService).assertCanWriteToBranch(branch.getId());
        var request = new CreateBatchRequest(product.getId(), branch.getId(), 5, BigDecimal.TEN, null, null);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(ForbiddenException.class);

        verify(stockMutationService, never()).receiveStock(any(), any(), anyInt(), any(), any(), any(), any(),
                any(), any(), any());
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
