package com.company.erp.sales;

import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.security.BranchAccessService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Spec section 43 explicitly calls out "User A cannot access User B's
 * branch" as its own test, separate from the general branch-authorization
 * suite, and section 48's final acceptance scenario ends on exactly this
 * check (a Bole Store user hitting Main Warehouse data -> 403). Every
 * other test in the codebase that touches branch scoping mocks
 * BranchAccessService wholesale, which proves each service CALLS it but
 * never proves what happens when it actually denies access. This is that
 * missing link, on the entity the acceptance scenario is actually about.
 */
@ExtendWith(MockitoExtension.class)
class SalesHistoryQueryServiceTest {

    @Mock private SaleRepository saleRepository;
    @Mock private BranchAccessService branchAccessService;

    private SalesHistoryQueryService service;

    private UUID mainWarehouseId;
    private Sale saleAtMainWarehouse;

    @BeforeEach
    void setUp() {
        service = new SalesHistoryQueryService(saleRepository, branchAccessService);

        mainWarehouseId = UUID.randomUUID();
        Branch mainWarehouse = new Branch();
        mainWarehouse.setName("Main Warehouse");
        setId(mainWarehouse, mainWarehouseId);

        saleAtMainWarehouse = new Sale();
        saleAtMainWarehouse.setSaleNumber("SALE-20260115-0001");
        saleAtMainWarehouse.setBranch(mainWarehouse);
        saleAtMainWarehouse.setTotalAmount(BigDecimal.valueOf(3000));
        saleAtMainWarehouse.setPaidAmount(BigDecimal.valueOf(3000));
        saleAtMainWarehouse.setRemainingAmount(BigDecimal.ZERO);
        saleAtMainWarehouse.setPaymentStatus(PaymentStatus.PAID);
        setId(saleAtMainWarehouse, UUID.randomUUID());
    }

    @Test
    void aUserWithAccessToTheSalesBranchCanReadIt() {
        when(saleRepository.findById(saleAtMainWarehouse.getId())).thenReturn(Optional.of(saleAtMainWarehouse));
        when(branchAccessService.resolveReadableBranchId(mainWarehouseId)).thenReturn(Optional.of(mainWarehouseId));

        var response = service.get(saleAtMainWarehouse.getId());

        assertThat(response.saleNumber()).isEqualTo("SALE-20260115-0001");
    }

    @Test
    void aBoleStoreUserCannotReadASaleThatBelongsToMainWarehouse() {
        when(saleRepository.findById(saleAtMainWarehouse.getId())).thenReturn(Optional.of(saleAtMainWarehouse));
        // This is exactly what BranchAccessService.resolveReadableBranchId
        // does for real when a non-admin's own branch doesn't match the
        // requested one (see its source) - mocked here as the documented
        // contract, not reimplemented, since BranchAccessService itself is
        // already unit-tested in BranchAccessServiceTest.
        when(branchAccessService.resolveReadableBranchId(mainWarehouseId))
                .thenThrow(ForbiddenException.branchAccessDenied(mainWarehouseId));

        assertThatThrownBy(() -> service.get(saleAtMainWarehouse.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void theServiceChecksTheSalesOwnBranchNeverATrustedCallerSuppliedOne() {
        // The whole point of the check: it must be keyed off sale.getBranch(),
        // not off anything the caller passed in - there IS nothing the
        // caller passes in here (get(id) takes no branchId argument at
        // all), which is itself the correct design. This test pins that
        // down explicitly so a future refactor can't quietly add a
        // trusted branchId parameter that bypasses the sale's real branch.
        when(saleRepository.findById(saleAtMainWarehouse.getId())).thenReturn(Optional.of(saleAtMainWarehouse));
        when(branchAccessService.resolveReadableBranchId(mainWarehouseId)).thenReturn(Optional.of(mainWarehouseId));

        service.get(saleAtMainWarehouse.getId());

        org.mockito.Mockito.verify(branchAccessService).resolveReadableBranchId(eq(mainWarehouseId));
    }

    @Test
    void gettingANonExistentSaleIsAResourceNotFoundNotAForbidden() {
        UUID missingId = UUID.randomUUID();
        when(saleRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(missingId))
                .isInstanceOf(ResourceNotFoundException.class);
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
