package com.company.erp.product;

import com.company.erp.common.audit.BaseEntity;
import com.company.erp.inventory.ProductStockService;
import com.company.erp.common.exception.DuplicateResourceException;
import com.company.erp.inventory.StockStatus;
import com.company.erp.productbranch.ProductBranchAvailabilityService;
import com.company.erp.product.dto.ProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductStockService productStockService;
    @Mock private ProductBranchAvailabilityService productBranchAvailabilityService;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepository, productStockService, productBranchAvailabilityService);
        lenient().when(productBranchAvailabilityService.allowedProductIdsAtAll(any())).thenReturn(null);

        lenient().when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void hideOutOfStockExcludesOutOfStockProductsButNeverThoseFoundElsewhere() {
        UUID inStockId = UUID.randomUUID();
        UUID outOfStockId = UUID.randomUUID();
        Product inStock = productWithSku("IN-1");
        Product outOfStock = productWithSku("OUT-1");
        setId(inStock, inStockId);
        setId(outOfStock, outOfStockId);

        Map<UUID, Integer> available = new HashMap<>();
        available.put(inStockId, 5);
        available.put(outOfStockId, 0);

        when(productStockService.availableByProduct(any(), isNull())).thenReturn(available);
        when(productStockService.idsWithStatus(available, StockStatus.OUT_OF_STOCK, true))
                .thenReturn(Set.of(outOfStockId));
        when(productRepository.findAll()).thenReturn(List.of(inStock, outOfStock));
        when(productRepository.search(eq(true), isNull(), any(), any()))
                .thenAnswer(inv -> new org.springframework.data.domain.PageImpl<>(List.of(inStock)));

        service.search(true, null, null, true,
                com.company.erp.security.BranchAccessService.BranchScope.ALL, null, null, PageRequest.of(0, 20));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Set<UUID>> idFilter = org.mockito.ArgumentCaptor.forClass(Set.class);
        verify(productRepository).search(eq(true), isNull(), idFilter.capture(), any());
        assertThat(idFilter.getValue()).containsExactly(inStockId);
    }

    @Test
    void createsAProductWhenTheSkuIsNew() {
        when(productRepository.existsBySkuIgnoreCase("CBL-2.5")).thenReturn(false);

        var response = service.create(new ProductRequest("Cable 2.5mm", "CBL-2.5", null,
                "pcs", 20));

        assertThat(response.name()).isEqualTo("Cable 2.5mm");
        assertThat(response.active()).isTrue();
    }

    @Test
    void rejectsCreationWhenTheSkuAlreadyExistsCaseInsensitively() {
        when(productRepository.existsBySkuIgnoreCase("cbl-2.5")).thenReturn(true);

        var request = new ProductRequest("Cable 2.5mm", "cbl-2.5", null, "pcs", 0);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void nullReorderLevelDefaultsToZeroRatherThanNpeOrNull() {
        when(productRepository.existsBySkuIgnoreCase(any())).thenReturn(false);

        var response = service.create(new ProductRequest("Cable 2.5mm", "CBL-2.5", null,
                "pcs", null));

        assertThat(response.reorderLevel()).isEqualTo(0);
    }

    @Test
    void updatingAProductToItsOwnSkuIsNotTreatedAsADuplicate() {
        Product existing = productWithSku("CBL-2.5");
        when(productRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        var request = new ProductRequest("Cable 2.5mm Updated", "CBL-2.5", null,
                "pcs", 10);

        // Should not throw - same SKU as before, just a renamed/repriced product.
        var response = service.update(existing.getId(), request);

        assertThat(response.name()).isEqualTo("Cable 2.5mm Updated");
        // existsBySkuIgnoreCase should never even be consulted since the SKU didn't change.
        org.mockito.Mockito.verify(productRepository, org.mockito.Mockito.never()).existsBySkuIgnoreCase(any());
    }

    @Test
    void updatingToAnotherProductsSkuIsRejected() {
        Product existing = productWithSku("CBL-2.5");
        when(productRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(productRepository.existsBySkuIgnoreCase("CBL-4.0")).thenReturn(true);

        var request = new ProductRequest("Cable 2.5mm", "CBL-4.0", null, "pcs", 0);

        assertThatThrownBy(() -> service.update(existing.getId(), request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deactivateIsASoftDeleteNotARealDelete() {
        Product existing = productWithSku("CBL-2.5");
        when(productRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        service.deactivate(existing.getId());

        assertThat(existing.isActive()).isFalse();
        org.mockito.Mockito.verify(productRepository, org.mockito.Mockito.never()).delete(any(Product.class));
        org.mockito.Mockito.verify(productRepository, org.mockito.Mockito.never()).deleteById(any());
    }

    private Product productWithSku(String sku) {
        Product product = new Product();
        product.setName("Cable 2.5mm");
        product.setSku(sku);
        product.setUnit("pcs");
        product.setReorderLevel(10);
        product.setActive(true);
        setId(product, UUID.randomUUID());
        return product;
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
