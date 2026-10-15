package com.company.erp.productbranch;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductBranchAvailabilityServiceTest {

    @Mock private ProductBranchAvailabilityRepository repository;
    @Mock private ProductRepository productRepository;
    @Mock private BranchRepository branchRepository;

    private ProductBranchAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new ProductBranchAvailabilityService(repository, productRepository, branchRepository);
    }

    @Test
    void seedingANewProductTicksItAtEveryExistingBranch() {
        UUID productId = UUID.randomUUID();
        Branch a = branch();
        Branch b = branch();
        when(branchRepository.findAll()).thenReturn(List.of(a, b));

        service.seedForNewProduct(productId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductBranchAvailability>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(ProductBranchAvailability::getBranchId)
                .containsExactlyInAnyOrder(a.getId(), b.getId());
        assertThat(saved.getValue()).allMatch(r -> r.getProductId().equals(productId));
    }

    @Test
    void seedingANewBranchTicksEveryExistingProductForIt() {
        UUID branchId = UUID.randomUUID();
        Product p1 = product();
        Product p2 = product();
        when(productRepository.findAll()).thenReturn(List.of(p1, p2));

        service.seedForNewBranch(branchId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductBranchAvailability>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(ProductBranchAvailability::getProductId)
                .containsExactlyInAnyOrder(p1.getId(), p2.getId());
    }

    @Test
    void tickingAnAlreadyTickedPairDoesNothing() {
        UUID productId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        when(repository.existsByProductIdAndBranchId(productId, branchId)).thenReturn(true);

        service.setAllowed(productId, branchId, true);

        verify(repository, never()).save(any());
    }

    @Test
    void untickingRemovesTheRowSoTheProductStopsBelongingThere() {
        UUID productId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();

        service.setAllowed(productId, branchId, false);

        verify(repository).deleteByProductIdAndBranchId(productId, branchId);
    }

    @Test
    void allowedEverywhereIsTheIntersectionAcrossAllGivenBranches() {
        UUID sourceBranch = UUID.randomUUID();
        UUID destBranch = UUID.randomUUID();
        UUID onlyAtSource = UUID.randomUUID();
        UUID atBoth = UUID.randomUUID();
        UUID onlyAtDest = UUID.randomUUID();
        when(repository.findProductIdsByBranchId(sourceBranch)).thenReturn(Set.of(onlyAtSource, atBoth));
        when(repository.findProductIdsByBranchId(destBranch)).thenReturn(Set.of(atBoth, onlyAtDest));

        assertThat(service.allowedProductIdsAtAll(List.of(sourceBranch, destBranch))).containsExactly(atBoth);
    }

    @Test
    void noBranchesGivenMeansNoRestrictionAtAll() {
        assertThat(service.allowedProductIdsAtAll(List.of())).isNull();
        assertThat(service.allowedProductIdsAtAll(null)).isNull();
    }

    private Branch branch() {
        Branch b = new Branch();
        setId(b, UUID.randomUUID());
        return b;
    }

    private Product product() {
        Product p = new Product();
        setId(p, UUID.randomUUID());
        return p;
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
