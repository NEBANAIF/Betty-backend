package com.company.erp.returns.customer;

import com.company.erp.batch.ProductBatch;
import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.loan.Loan;
import com.company.erp.loan.LoanRepository;
import com.company.erp.loan.LoanService;
import com.company.erp.product.Product;
import com.company.erp.returns.customer.dto.CreateCustomerReturnItemRequest;
import com.company.erp.returns.customer.dto.CreateCustomerReturnRequest;
import com.company.erp.sales.Sale;
import com.company.erp.sales.SaleBatchAllocation;
import com.company.erp.sales.SaleItem;
import com.company.erp.sales.SaleRepository;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.UserPrincipal;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.stockhistory.StockReferenceType;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerReturnServiceTest {

    @Mock private CustomerReturnRepository customerReturnRepository;
    @Mock private CustomerReturnItemRepository customerReturnItemRepository;
    @Mock private SaleRepository saleRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchAccessService branchAccessService;
    @Mock private StockMutationService stockMutationService;
    @Mock private com.company.erp.finance.FinancialTransactionService financialTransactionService;
    @Mock private LoanRepository loanRepository;
    @Mock private LoanService loanService;

    private CustomerReturnService service;

    private Branch boleStore;
    private Product product;
    private ProductBatch batch;
    private Sale sale;
    private SaleBatchAllocation allocation;
    private UUID saleId;
    private User cashier;

    @BeforeEach
    void setUp() {
        service = new CustomerReturnService(customerReturnRepository, customerReturnItemRepository,
                saleRepository, userRepository, branchAccessService, stockMutationService,
                financialTransactionService, loanRepository, loanService);

        boleStore = new Branch();
        boleStore.setName("Bole Store");
        setId(boleStore, UUID.randomUUID());

        product = new Product();
        product.setName("Cable 2.5mm");
        setId(product, UUID.randomUUID());

        batch = new ProductBatch();
        batch.setBatchNumber("BATCH-BOLE-0001");
        batch.setCostPrice(BigDecimal.valueOf(110));
        setId(batch, UUID.randomUUID());

        cashier = new User();
        cashier.setName("Bole Store Staff");
        cashier.setRole(Role.STORE_STAFF);
        setId(cashier, UUID.randomUUID());

        saleId = UUID.randomUUID();
        sale = new Sale();
        setId(sale, saleId);
        sale.setSaleNumber("SALE-20260115-1234");
        sale.setBranch(boleStore);
        sale.setCustomer(null);

        SaleItem saleItem = new SaleItem();
        saleItem.setProduct(product);
        saleItem.setQuantity(20);
        saleItem.setUnitPrice(BigDecimal.valueOf(150));
        saleItem.setLineTotal(BigDecimal.valueOf(3000));
        sale.addItem(saleItem);

        allocation = new SaleBatchAllocation();
        allocation.setBatch(batch);
        allocation.setQuantityAllocated(20);
        allocation.setUnitCost(BigDecimal.valueOf(110));
        setId(allocation, UUID.randomUUID());
        saleItem.addAllocation(allocation);

        UserPrincipal principal = new UserPrincipal(cashier);
        lenient().when(branchAccessService.currentUser()).thenReturn(principal);
        lenient().when(userRepository.findById(any())).thenReturn(Optional.of(cashier));
        lenient().when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));
        lenient().when(customerReturnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(customerReturnItemRepository.sumReturnedForAllocation(any())).thenReturn(0);
    }

    @Test
    void refundsAtSaleUnitPriceNotBatchCostAndRestocksTheExactOriginalBatch() {
        var request = new CreateCustomerReturnRequest(saleId,
                List.of(new CreateCustomerReturnItemRequest(allocation.getId(), 5, true)));

        var result = service.create(request);

        // 5 units at the 150 the customer paid, not the 110 batch cost.
        assertThat(result.refundAmount()).isEqualByComparingTo("750");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).restocked()).isTrue();

        verify(stockMutationService).restockToBatch(
                eq(product), eq(boleStore), eq(batch.getId()), eq(5),
                eq(StockMovementType.CUSTOMER_RETURN), any(), eq(cashier),
                eq(StockReferenceType.CUSTOMER_RETURN), eq(saleId));
    }

    @Test
    void writtenOffReturnRefundsWithoutTouchingStock() {
        var request = new CreateCustomerReturnRequest(saleId,
                List.of(new CreateCustomerReturnItemRequest(allocation.getId(), 5, false)));

        var result = service.create(request);

        assertThat(result.refundAmount()).isEqualByComparingTo("750");
        assertThat(result.items().get(0).restocked()).isFalse();
        verify(stockMutationService, never()).restockToBatch(any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void cannotReturnMoreThanWasOriginallyAllocated() {
        var request = new CreateCustomerReturnRequest(saleId,
                List.of(new CreateCustomerReturnItemRequest(allocation.getId(), 21, true)));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void secondReturnIsCappedByWhatsAlreadyBeenReturnedOnTheSameAllocation() {
        when(customerReturnItemRepository.sumReturnedForAllocation(allocation.getId())).thenReturn(18);

        var request = new CreateCustomerReturnRequest(saleId,
                List.of(new CreateCustomerReturnItemRequest(allocation.getId(), 5, true)));

        // 18 already returned + 5 more > 20 originally allocated.
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void anAllocationFromAnotherSaleIsRejected() {
        UUID foreignAllocationId = UUID.randomUUID();
        var request = new CreateCustomerReturnRequest(saleId,
                List.of(new CreateCustomerReturnItemRequest(foreignAllocationId, 1, true)));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void restockedReturnPostsNegativeRevenueNegativeCogsAndAPayout() {
        var request = new CreateCustomerReturnRequest(saleId,
                List.of(new CreateCustomerReturnItemRequest(allocation.getId(), 5, true)));

        service.create(request);

        verify(financialTransactionService).record(eq(boleStore),
                eq(com.company.erp.finance.FinancialTransactionType.SALE_REVENUE),
                eq(BigDecimal.valueOf(-750)), any(), eq(com.company.erp.finance.FinancialReferenceType.CUSTOMER_RETURN), any());
        verify(financialTransactionService).record(eq(boleStore),
                eq(com.company.erp.finance.FinancialTransactionType.PAYMENT_OUT),
                eq(BigDecimal.valueOf(750)), any(), eq(com.company.erp.finance.FinancialReferenceType.CUSTOMER_RETURN), any());
        // 5 units * 110 batch cost, reversed since these units were restocked.
        verify(financialTransactionService).record(eq(boleStore),
                eq(com.company.erp.finance.FinancialTransactionType.COGS),
                eq(BigDecimal.valueOf(-550)), any(), eq(com.company.erp.finance.FinancialReferenceType.CUSTOMER_RETURN), any());
    }

    @Test
    void writtenOffReturnPostsRevenueReversalAndPayoutButNoCogsReversal() {
        var request = new CreateCustomerReturnRequest(saleId,
                List.of(new CreateCustomerReturnItemRequest(allocation.getId(), 5, false)));

        service.create(request);

        verify(financialTransactionService).record(eq(boleStore),
                eq(com.company.erp.finance.FinancialTransactionType.SALE_REVENUE),
                eq(BigDecimal.valueOf(-750)), any(), eq(com.company.erp.finance.FinancialReferenceType.CUSTOMER_RETURN), any());
        verify(financialTransactionService, never()).record(eq(boleStore),
                eq(com.company.erp.finance.FinancialTransactionType.COGS), any(), any(), any(), any());
    }

    @Test
    void returnAgainstAnUnpaidCreditSaleWritesDownTheLoanInsteadOfPostingACashRefund() {
        // The sale was never paid at all (fully on credit) - a Loan for
        // the full 3000 exists. Returning 5 units worth 750 should reduce
        // that Loan by 750, and post NO PAYMENT_OUT at all, since no cash
        // was ever collected to refund.
        sale.setRemainingAmount(BigDecimal.valueOf(3000));
        Loan loan = new Loan();
        loan.setOriginalAmount(BigDecimal.valueOf(3000));
        loan.setPaidAmount(BigDecimal.ZERO);
        loan.setRemainingAmount(BigDecimal.valueOf(3000));
        when(loanRepository.findBySaleId(saleId)).thenReturn(Optional.of(loan));
        when(loanService.reduceForReturn(loan, BigDecimal.valueOf(750))).thenReturn(BigDecimal.valueOf(750));

        var request = new CreateCustomerReturnRequest(saleId,
                List.of(new CreateCustomerReturnItemRequest(allocation.getId(), 5, true)));

        service.create(request);

        verify(loanService).reduceForReturn(loan, BigDecimal.valueOf(750));
        verify(financialTransactionService, never()).record(eq(boleStore),
                eq(com.company.erp.finance.FinancialTransactionType.PAYMENT_OUT), any(), any(), any(), any());
        // Revenue still reverses in full - it was recognized in full at sale time regardless of payment status.
        verify(financialTransactionService).record(eq(boleStore),
                eq(com.company.erp.finance.FinancialTransactionType.SALE_REVENUE),
                eq(BigDecimal.valueOf(-750)), any(), any(), any());
    }

    @Test
    void fullyReturningAWhollyUnpaidCreditSaleWritesDownTheEntireLoan() {
        // The exact edge case that required V11's migration relaxing
        // loans.original_amount's CHECK from > 0 to >= 0: returning
        // EVERYTHING from a sale that was never paid at all must write the
        // loan down to zero, not just partially - and must never produce a
        // cash refund, since no cash was ever collected in the first place.
        sale.setRemainingAmount(BigDecimal.valueOf(3000));
        Loan loan = new Loan();
        loan.setOriginalAmount(BigDecimal.valueOf(3000));
        loan.setPaidAmount(BigDecimal.ZERO);
        loan.setRemainingAmount(BigDecimal.valueOf(3000));
        when(loanRepository.findBySaleId(saleId)).thenReturn(Optional.of(loan));
        when(loanService.reduceForReturn(loan, BigDecimal.valueOf(3000))).thenReturn(BigDecimal.valueOf(3000));

        // All 20 allocated units, not just 5 - a full return.
        var request = new CreateCustomerReturnRequest(saleId,
                List.of(new CreateCustomerReturnItemRequest(allocation.getId(), 20, true)));

        service.create(request);

        verify(loanService).reduceForReturn(loan, BigDecimal.valueOf(3000));
        verify(financialTransactionService, never()).record(eq(boleStore),
                eq(com.company.erp.finance.FinancialTransactionType.PAYMENT_OUT), any(), any(), any(), any());
        verify(financialTransactionService).record(eq(boleStore),
                eq(com.company.erp.finance.FinancialTransactionType.SALE_REVENUE),
                eq(BigDecimal.valueOf(-3000)), any(), any(), any());
    }

    @Test
    void returnAgainstAPartiallyPaidCreditSaleSplitsBetweenLoanWriteDownAndCashRefund() {
        // 1000 of the 3000 was already paid in cash; 2000 remains on the
        // loan. Returning 5 units worth 750 should first shrink the loan
        // (capped at whatever it can absorb) and only refund in cash
        // whatever's left over - here the loan can absorb the whole 750,
        // so still no cash refund.
        sale.setRemainingAmount(BigDecimal.valueOf(2000));
        Loan loan = new Loan();
        loan.setOriginalAmount(BigDecimal.valueOf(2000));
        loan.setPaidAmount(BigDecimal.valueOf(1000));
        loan.setRemainingAmount(BigDecimal.valueOf(2000));
        when(loanRepository.findBySaleId(saleId)).thenReturn(Optional.of(loan));
        when(loanService.reduceForReturn(loan, BigDecimal.valueOf(750))).thenReturn(BigDecimal.valueOf(750));

        var request = new CreateCustomerReturnRequest(saleId,
                List.of(new CreateCustomerReturnItemRequest(allocation.getId(), 5, true)));

        service.create(request);

        verify(loanService).reduceForReturn(loan, BigDecimal.valueOf(750));
        verify(financialTransactionService, never()).record(eq(boleStore),
                eq(com.company.erp.finance.FinancialTransactionType.PAYMENT_OUT), any(), any(), any(), any());
    }

    @Test
    void returnExceedingTheRemainingLoanBalanceRefundsOnlyTheExcessInCash() {
        // Only 200 is still owed on the loan; the return is worth 750.
        // 200 writes down the loan to zero, and the remaining 550 - which
        // really was collected as cash for the rest of the sale - is a
        // real PAYMENT_OUT.
        sale.setRemainingAmount(BigDecimal.valueOf(200));
        Loan loan = new Loan();
        loan.setOriginalAmount(BigDecimal.valueOf(200));
        loan.setPaidAmount(BigDecimal.ZERO);
        loan.setRemainingAmount(BigDecimal.valueOf(200));
        when(loanRepository.findBySaleId(saleId)).thenReturn(Optional.of(loan));
        when(loanService.reduceForReturn(loan, BigDecimal.valueOf(750))).thenReturn(BigDecimal.valueOf(200));

        var request = new CreateCustomerReturnRequest(saleId,
                List.of(new CreateCustomerReturnItemRequest(allocation.getId(), 5, true)));

        service.create(request);

        verify(financialTransactionService).record(eq(boleStore),
                eq(com.company.erp.finance.FinancialTransactionType.PAYMENT_OUT),
                eq(BigDecimal.valueOf(550)), any(), any(), any());
    }

    @Test
    void aFullyPaidSaleStillRefundsInCashAndNeverTouchesLoans() {
        // remainingAmount defaults to null in setUp (no credit involved) -
        // loanRepository must never even be consulted.
        var request = new CreateCustomerReturnRequest(saleId,
                List.of(new CreateCustomerReturnItemRequest(allocation.getId(), 5, true)));

        service.create(request);

        verify(loanRepository, never()).findBySaleId(any());
        verify(financialTransactionService).record(eq(boleStore),
                eq(com.company.erp.finance.FinancialTransactionType.PAYMENT_OUT),
                eq(BigDecimal.valueOf(750)), any(), any(), any());
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
