package com.company.erp.inventory;

import com.company.erp.batch.ProductBatch;
import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.inventory.dto.ProductCostRow;
import com.company.erp.inventory.dto.ProductCostsResponse;
import com.company.erp.product.Product;
import com.company.erp.security.BranchAccessService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito mocks (no strict-stubbing extension) - these tests only care
 * about the arithmetic over batches, not about how each mock was touched.
 */
class ProductCostServiceTest {

    private final ProductBatchRepository productBatchRepository = mock(ProductBatchRepository.class);
    private final BranchAccessService branchAccessService = mock(BranchAccessService.class);
    private final ProductCostService service = new ProductCostService(productBatchRepository, branchAccessService);

    @Test
    void sameProductBoughtAtTwoCostsKeepsBothCostsAndValuesEachUnitAtItsOwnCost() {
        UUID branchId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(productId);

        // Bought a week ago: 10 units at 10. Bought today: 20 units at 12.
        // Repository contract: oldest batch first.
        ProductBatch older = batch(product, 10, "10.00");
        ProductBatch newer = batch(product, 20, "12.00");

        when(branchAccessService.resolveReadableBranchIds(any(), any())).thenReturn(List.of(branchId));
        when(productBatchRepository.findInStockForBranches(any())).thenReturn(List.of(older, newer));

        ProductCostsResponse response = service.getCosts(BranchAccessService.BranchScope.ALL, null);

        assertThat(response.rows()).hasSize(1);
        ProductCostRow row = response.rows().get(0);
        assertThat(row.productId()).isEqualTo(productId);
        assertThat(row.quantityOnHand()).isEqualTo(30);
        assertThat(row.oldestCost()).isEqualByComparingTo("10.00");
        assertThat(row.latestCost()).isEqualByComparingTo("12.00");
        // 10 x 10 + 20 x 12 = 340 - NOT 30 x 12 (which would overstate it as 360).
        assertThat(row.stockValue()).isEqualByComparingTo("340.00");
        assertThat(response.totalStockValue()).isEqualByComparingTo("340.00");
    }

    @Test
    void totalIsTheSumAcrossProducts() {
        UUID branchId = UUID.randomUUID();
        Product first = mock(Product.class);
        Product second = mock(Product.class);
        when(first.getId()).thenReturn(UUID.randomUUID());
        when(second.getId()).thenReturn(UUID.randomUUID());

        when(branchAccessService.resolveReadableBranchIds(any(), any())).thenReturn(List.of(branchId));
        when(productBatchRepository.findInStockForBranches(any())).thenReturn(List.of(
                batch(first, 5, "20.00"),
                batch(second, 4, "2.50")));

        ProductCostsResponse response = service.getCosts(BranchAccessService.BranchScope.ALL, null);

        assertThat(response.rows()).hasSize(2);
        // 5 x 20 + 4 x 2.50 = 110
        assertThat(response.totalStockValue()).isEqualByComparingTo("110.00");
    }

    @Test
    void noAccessibleBranchesGivesZeroWithoutQueryingBatches() {
        when(branchAccessService.resolveReadableBranchIds(any(), any())).thenReturn(List.of());

        ProductCostsResponse response = service.getCosts(BranchAccessService.BranchScope.STORES, null);

        assertThat(response.rows()).isEmpty();
        assertThat(response.totalStockValue()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(productBatchRepository, never()).findInStockForBranches(any());
    }

    private ProductBatch batch(Product product, int remaining, String cost) {
        ProductBatch batch = mock(ProductBatch.class);
        when(batch.getProduct()).thenReturn(product);
        when(batch.getRemainingQuantity()).thenReturn(remaining);
        when(batch.getCostPrice()).thenReturn(new BigDecimal(cost));
        return batch;
    }
}
