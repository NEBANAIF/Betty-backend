package com.company.erp.inventory;

import com.company.erp.common.audit.BaseEntity;
import com.company.erp.inventory.dto.StockAttentionRow;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.security.BranchAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductStockServiceTest {

    private static final BranchAccessService.BranchScope ALL = BranchAccessService.BranchScope.ALL;

    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private BranchAccessService branchAccessService;

    private ProductStockService service;

    private final UUID branchId = UUID.randomUUID();
    private Product cable;      // 50 available, reorder at 10  -> in stock
    private Product lamp;       // 4 available, reorder at 5    -> low
    private Product bulb;       // 5 available, reorder at 5    -> low (at the level counts)
    private Product pipe;       // 0 available, reorder at 0    -> out
    private Product unstocked;  // no stock row at all          -> out
    private Product retired;    // inactive, 0 available        -> not counted

    @BeforeEach
    void setUp() {
        service = new ProductStockService(inventoryRepository, productRepository, branchAccessService);
        cable = product("Cable", 10, true);
        lamp = product("Lamp", 5, true);
        bulb = product("Bulb", 5, true);
        pipe = product("Pipe", 0, true);
        unstocked = product("Unstocked", 3, true);
        retired = product("Retired", 2, false);
    }

    private void givenStock() {
        when(branchAccessService.resolveReadableBranchIds(ALL, null)).thenReturn(List.of(branchId));
        when(inventoryRepository.sumAvailableByProduct(List.of(branchId))).thenReturn(List.of(
                new Object[]{cable.getId(), 50L},
                new Object[]{lamp.getId(), 4L},
                new Object[]{bulb.getId(), 5L},
                new Object[]{pipe.getId(), 0L},
                new Object[]{retired.getId(), 0L}));
        when(productRepository.findAll()).thenReturn(List.of(cable, lamp, bulb, pipe, unstocked, retired));
    }

    @Test
    void summaryCountsActiveProductsIntoTheThreeBuckets() {
        givenStock();

        var summary = service.summary(ALL, null);

        assertThat(summary.total()).isEqualTo(5);
        assertThat(summary.inStock()).isEqualTo(1);
        assertThat(summary.lowStock()).isEqualTo(2);
        assertThat(summary.outOfStock()).isEqualTo(2); // pipe, and the product with no stock row
    }

    @Test
    void reportListsOutOfStockFirstThenLowestFirstAndLeavesOutWhatIsFine() {
        givenStock();

        var report = service.report(ALL, null);

        assertThat(report.needsAttention()).extracting(StockAttentionRow::productName)
                .containsExactly("Pipe", "Unstocked", "Lamp", "Bulb");
        assertThat(report.needsAttention().get(2).status()).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(report.summary().inStock()).isEqualTo(1);
    }

    @Test
    void idsWithStatusIsWhatTheProductListFilterUses() {
        givenStock();
        Map<UUID, Integer> available = service.availableByProduct(ALL, null);

        assertThat(service.idsWithStatus(available, StockStatus.LOW_STOCK, true))
                .containsExactlyInAnyOrder(lamp.getId(), bulb.getId());
        assertThat(service.idsWithStatus(available, StockStatus.OUT_OF_STOCK, true))
                .containsExactlyInAnyOrder(pipe.getId(), unstocked.getId());
        // an inactive product only shows up when inactive ones are wanted too
        assertThat(service.idsWithStatus(available, StockStatus.OUT_OF_STOCK, false))
                .contains(retired.getId());
    }

    @Test
    void noReadableBranchesMeansNoStockRowsAreQueried() {
        when(branchAccessService.resolveReadableBranchIds(ALL, null)).thenReturn(List.of());

        assertThat(service.availableByProduct(ALL, null)).isEmpty();

        verifyNoInteractions(inventoryRepository);
    }

    private Product product(String name, int reorderLevel, boolean active) {
        Product p = new Product();
        p.setName(name);
        p.setSku(name.toUpperCase());
        p.setReorderLevel(reorderLevel);
        p.setActive(active);
        try {
            var field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(p, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return p;
    }
}
