package com.company.erp.product.importer;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.DuplicateResourceException;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.product.importer.dto.ProductImportRow;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.UserPrincipal;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.user.Role;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductImportRowServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchAccessService branchAccessService;
    @Mock private StockMutationService stockMutationService;

    private ProductImportRowService service;
    private Branch warehouseB;
    private Product cable;

    @BeforeEach
    void setUp() {
        service = new ProductImportRowService(productRepository, branchRepository, userRepository,
                branchAccessService, stockMutationService);

        warehouseB = new Branch();
        warehouseB.setName("Warehouse B");
        setId(warehouseB, UUID.randomUUID());

        cable = new Product();
        cable.setName("Power Cable");
        cable.setSku("PC-1");
        cable.setUnit("pcs");
        cable.setActive(true);
        setId(cable, UUID.randomUUID());

        User user = new User();
        user.setName("Admin");
        user.setRole(Role.SUPER_ADMIN);
        setId(user, UUID.randomUUID());
        lenient().when(branchAccessService.currentUser()).thenReturn(new UserPrincipal(user));
        lenient().when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        lenient().when(branchRepository.findByNameIgnoreCase("Warehouse B")).thenReturn(Optional.of(warehouseB));
    }

    private ProductImportRow row(String name, String sku, String branch, Integer stock) {
        return new ProductImportRow(2, name, sku, "pcs", null, 5, branch, stock, new BigDecimal("40.00"));
    }

    @Test
    void aSecondRowWithTheSameSkuAddsStockAtAnotherBranchInsteadOfCreatingAnotherProduct() {
        when(productRepository.findBySkuIgnoreCase("PC-1")).thenReturn(Optional.of(cable));

        String message = service.importRow(row("power cable", "pc-1", "Warehouse B", 80));

        assertThat(message).startsWith("Existing product - added 80 pcs").contains("Warehouse B");
        verify(productRepository, never()).save(any());
        verify(stockMutationService).receiveStock(eq(cable), eq(warehouseB), eq(80), eq(new BigDecimal("40.00")),
                any(), eq(StockMovementType.ADJUSTMENT), any(), any(), any(), isNull());
    }

    @Test
    void aNewSkuStillCreatesTheProduct() {
        when(productRepository.findBySkuIgnoreCase("PC-1")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        String message = service.importRow(row("Power Cable", "PC-1", "Warehouse B", 100));

        assertThat(message).startsWith("Product created with 100 pcs");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void theSameSkuUnderADifferentNameIsRefused() {
        when(productRepository.findBySkuIgnoreCase("PC-1")).thenReturn(Optional.of(cable));

        assertThatThrownBy(() -> service.importRow(row("Extension Lead", "PC-1", "Warehouse B", 10)))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("Power Cable");
        verify(stockMutationService, never()).receiveStock(any(), any(), anyInt(), any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void anExistingSkuWithNoStockHasNothingToAdd() {
        when(productRepository.findBySkuIgnoreCase("PC-1")).thenReturn(Optional.of(cable));

        assertThatThrownBy(() -> service.importRow(row("Power Cable", "PC-1", "Warehouse B", null)))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("nothing to add");
    }

    @Test
    void aDeletedProductIsNotRevivedByAnImport() {
        cable.setActive(false);
        when(productRepository.findBySkuIgnoreCase("PC-1")).thenReturn(Optional.of(cable));

        assertThatThrownBy(() -> service.importRow(row("Power Cable", "PC-1", "Warehouse B", 10)))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("deleted");
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
