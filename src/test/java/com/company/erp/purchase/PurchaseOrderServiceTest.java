package com.company.erp.purchase;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.branch.BranchType;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.purchase.dto.ReceivePurchaseItemRequest;
import com.company.erp.purchase.dto.ReceivePurchaseRequest;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.UserPrincipal;
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
class PurchaseOrderServiceTest {

    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchAccessService branchAccessService;
    @Mock private StockMutationService stockMutationService;

    private PurchaseOrderService service;

    private final UUID branchId = UUID.randomUUID();
    private Branch branch;
    private Product product;
    private User adminUser;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderService(purchaseOrderRepository, supplierRepository, branchRepository,
                productRepository, userRepository, branchAccessService, stockMutationService);

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

    private PurchaseOrder approvedPoWithOneItem(int quantity, int alreadyReceived) {
        PurchaseOrder po = new PurchaseOrder();
        setId(po, UUID.randomUUID());
        po.setBranch(branch);
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
