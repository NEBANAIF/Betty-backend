package com.company.erp.pos;

import com.company.erp.bank.Bank;
import com.company.erp.bank.BankRepository;
import com.company.erp.bank.BankResolver;
import com.company.erp.batch.BatchConsumption;
import com.company.erp.batch.ProductBatch;
import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.customer.Customer;
import com.company.erp.customer.CustomerRepository;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.loan.LoanService;
import com.company.erp.notification.NotificationService;
import com.company.erp.payment.Payment;
import com.company.erp.payment.PaymentMethod;
import com.company.erp.payment.PaymentRepository;
import com.company.erp.pos.dto.CheckoutItemRequest;
import com.company.erp.pos.dto.CheckoutRequest;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.roles.AccessRole;
import com.company.erp.sales.SaleRepository;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.UserPrincipal;
import com.company.erp.user.Role;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosCheckoutServiceTest {

    @Mock private BranchAccessService branchAccessService;
    @Mock private BranchRepository branchRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;
    @Mock private StockMutationService stockMutationService;
    @Mock private SaleRepository saleRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ProductBatchRepository productBatchRepository;
    @Mock private LoanService loanService;
    @Mock private NotificationService notificationService;
    @Mock private BankRepository bankRepository;
    @Mock private com.company.erp.finance.FinancialTransactionService financialTransactionService;

    private PosCheckoutService service;

    private final UUID branchId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private Branch branch;
    private Product product;
    private User cashier;

    @BeforeEach
    void setUp() {
        service = new PosCheckoutService(branchAccessService, branchRepository, productRepository,
                customerRepository, userRepository, stockMutationService, saleRepository,
                paymentRepository, productBatchRepository, loanService, financialTransactionService,
                new BankResolver(bankRepository), notificationService);

        branch = new Branch();
        branch.setName("Bole Store");

        product = new Product();
        product.setName("Cable 2.5mm");

        cashier = new User();
        cashier.setName("Bole Cashier");
        cashier.setRole(Role.STORE_STAFF);

        UserPrincipal principal = new UserPrincipal(cashier);
        lenient().when(branchAccessService.currentUser()).thenReturn(principal);
        lenient().when(userRepository.findById(any())).thenReturn(Optional.of(cashier));
        lenient().when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
        lenient().when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        lenient().when(saleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saleRepository.existsBySaleNumber(any())).thenReturn(false);

        ProductBatch batch = new ProductBatch();
        batch.setBatchNumber("BATCH-BOLE-0001");
        lenient().when(productBatchRepository.getReferenceById(any())).thenReturn(batch);
    }

    @Test
    void acceptanceScenario_twentyUnitsAt150FromStockCosted110_yieldsRevenue3000Cogs2200GrossProfit800() {
        when(stockMutationService.issueStock(any(), any(), org.mockito.ArgumentMatchers.eq(20), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new BatchConsumption(UUID.randomUUID(), "BATCH-BOLE-0001", 20, BigDecimal.valueOf(110))));

        var request = new CheckoutRequest(
                branchId, null, List.of(new CheckoutItemRequest(productId, 20, BigDecimal.valueOf(150), null)),
                null, PaymentMethod.CASH, BigDecimal.valueOf(3000), null, null, null, null);

        var result = service.checkout(request);

        assertThat(result.totalAmount()).isEqualByComparingTo("3000");
        assertThat(result.cogs()).isEqualByComparingTo("2200");
        assertThat(result.grossProfit()).isEqualByComparingTo("800");
        assertThat(result.paymentStatus()).isEqualTo("PAID");
        assertThat(result.remainingAmount()).isEqualByComparingTo("0");
    }

    @Test
    void partialPaymentWithoutCustomerIsRejected() {
        when(stockMutationService.issueStock(any(), any(), any(Integer.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new BatchConsumption(UUID.randomUUID(), "BATCH-BOLE-0001", 20, BigDecimal.valueOf(110))));

        var request = new CheckoutRequest(
                branchId, null, List.of(new CheckoutItemRequest(productId, 20, BigDecimal.valueOf(150), null)),
                null, PaymentMethod.CASH, BigDecimal.valueOf(1000), null, null, null, null); // total is 3000, paying only 1000

        assertThatThrownBy(() -> service.checkout(request))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void partialPaymentWithCustomerIsAllowedAndTracksRemainingBalance() {
        Customer customer = new Customer();
        customer.setName("Walk-in Regular");
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(stockMutationService.issueStock(any(), any(), any(Integer.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new BatchConsumption(UUID.randomUUID(), "BATCH-BOLE-0001", 20, BigDecimal.valueOf(110))));

        var request = new CheckoutRequest(
                branchId, customerId, List.of(new CheckoutItemRequest(productId, 20, BigDecimal.valueOf(150), null)),
                null, PaymentMethod.CASH, BigDecimal.valueOf(1000), null, null, null, null);

        var result = service.checkout(request);

        assertThat(result.paymentStatus()).isEqualTo("PARTIAL");
        assertThat(result.remainingAmount()).isEqualByComparingTo("2000");
    }

    @Test
    void zeroTotalSaleAfterFullDiscountIsPaidNotCredit() {
        var request = new CheckoutRequest(
                branchId, null, List.of(new CheckoutItemRequest(productId, 20, BigDecimal.ZERO, null)),
                BigDecimal.ZERO, PaymentMethod.CASH, BigDecimal.ZERO, null, null, null, null);

        when(stockMutationService.issueStock(any(), any(), org.mockito.ArgumentMatchers.eq(20), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new BatchConsumption(UUID.randomUUID(), "BATCH-BOLE-0001", 20, BigDecimal.valueOf(110))));

        var result = service.checkout(request);

        assertThat(result.totalAmount()).isEqualByComparingTo("0");
        assertThat(result.paymentStatus()).isEqualTo("PAID");
        assertThat(result.remainingAmount()).isEqualByComparingTo("0");
    }

    @Test
    void sellingFromAChosenBatchCostsTheSaleAtThatBatchAndSkipsFifo() {
        UUID chosenBatchId = UUID.randomUUID();
        // The newer 12-birr batch was chosen on purpose, so COGS must be 20 x 12 = 240
        // even though an older 10-birr batch would have been taken first by FIFO.
        when(stockMutationService.issueFromBatch(any(), any(), org.mockito.ArgumentMatchers.eq(chosenBatchId),
                org.mockito.ArgumentMatchers.eq(20), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new BatchConsumption(chosenBatchId, "BATCH-BOLE-0002", 20, BigDecimal.valueOf(12))));

        var request = new CheckoutRequest(
                branchId, null,
                List.of(new CheckoutItemRequest(productId, 20, BigDecimal.valueOf(15), chosenBatchId)),
                null, PaymentMethod.CASH, BigDecimal.valueOf(300), null, null, null, null);

        var result = service.checkout(request);

        assertThat(result.totalAmount()).isEqualByComparingTo("300");
        assertThat(result.cogs()).isEqualByComparingTo("240");
        assertThat(result.grossProfit()).isEqualByComparingTo("60");
        org.mockito.Mockito.verify(stockMutationService, org.mockito.Mockito.never())
                .issueStock(any(), any(), any(Integer.class), any(), any(), any(), any(), any());
    }

    @Test
    void aSaleLineWithoutAPriceIsRejectedBecauseProductsHaveNoStoredPrice() {
        var request = new CheckoutRequest(
                branchId, null, List.of(new CheckoutItemRequest(productId, 20, null, null)),
                null, PaymentMethod.CASH, BigDecimal.ZERO, null, null, null, null);

        assertThatThrownBy(() -> service.checkout(request))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    /** A caller whose stored role grants only the basics - no discounts, no choosing a batch. */
    private UserPrincipal limitedCashier() {
        AccessRole basics = new AccessRole();
        basics.setName("Basics only");
        basics.setBaseRole(Role.STORE_STAFF);
        basics.setPermissions(new HashSet<>(Set.of("POS_ACCESS", "SALES_CREATE")));
        User limited = new User();
        limited.setName("Limited Cashier");
        limited.setRole(Role.STORE_STAFF);
        limited.setAccessRole(basics);
        return new UserPrincipal(limited);
    }

    @Test
    void aRoleWithoutTheDiscountPermissionCannotGiveADiscount() {
        lenient().when(branchAccessService.currentUser()).thenReturn(limitedCashier());

        var request = new CheckoutRequest(
                branchId, null, List.of(new CheckoutItemRequest(productId, 20, BigDecimal.valueOf(150), null)),
                BigDecimal.TEN, PaymentMethod.CASH, BigDecimal.valueOf(2990), null, null, null, null);

        assertThatThrownBy(() -> service.checkout(request)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aRoleWithoutTheBatchPermissionCannotChooseTheBatchToSellFrom() {
        lenient().when(branchAccessService.currentUser()).thenReturn(limitedCashier());

        var request = new CheckoutRequest(
                branchId, null, List.of(new CheckoutItemRequest(productId, 20, BigDecimal.valueOf(150), UUID.randomUUID())),
                null, PaymentMethod.CASH, BigDecimal.valueOf(3000), null, null, null, null);

        assertThatThrownBy(() -> service.checkout(request)).isInstanceOf(ForbiddenException.class);
    }

    private Bank registeredBank(boolean active) {
        Bank bank = new Bank();
        bank.setName("Commercial Bank of Ethiopia");
        bank.setAccountNumber("1000123456789");
        bank.setActive(active);
        return bank;
    }

    private CheckoutRequest bankSale(UUID bankId, String bankName, String reference) {
        return new CheckoutRequest(
                branchId, null, List.of(new CheckoutItemRequest(productId, 20, BigDecimal.valueOf(150), null)),
                null, PaymentMethod.BANK, BigDecimal.valueOf(3000), bankName, reference, null, bankId);
    }

    private void stockIsAvailable() {
        when(stockMutationService.issueStock(any(), any(), any(Integer.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new BatchConsumption(UUID.randomUUID(), "BATCH-BOLE-0001", 20, BigDecimal.valueOf(110))));
    }

    @Test
    void aBankPaymentIsLinkedToTheChosenRegisteredBank() {
        UUID bankId = UUID.randomUUID();
        Bank bank = registeredBank(true);
        when(bankRepository.findById(bankId)).thenReturn(Optional.of(bank));
        stockIsAvailable();

        service.checkout(bankSale(bankId, null, "TX-778"));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getBank()).isSameAs(bank);
        assertThat(saved.getValue().getBankName()).isEqualTo("Commercial Bank of Ethiopia");
        assertThat(saved.getValue().getAccountReference()).isEqualTo("TX-778");
    }

    @Test
    void aBankPaymentWithNoBankChosenIsRejected() {
        stockIsAvailable();

        assertThatThrownBy(() -> service.checkout(bankSale(null, null, "TX-778")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void aDeactivatedBankCannotReceiveAPayment() {
        UUID bankId = UUID.randomUUID();
        when(bankRepository.findById(bankId)).thenReturn(Optional.of(registeredBank(false)));
        stockIsAvailable();

        assertThatThrownBy(() -> service.checkout(bankSale(bankId, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void aCashPaymentCarriesNoBankEvenIfOneIsSent() {
        stockIsAvailable();
        var request = new CheckoutRequest(
                branchId, null, List.of(new CheckoutItemRequest(productId, 20, BigDecimal.valueOf(150), null)),
                null, PaymentMethod.CASH, BigDecimal.valueOf(3000), "Some Bank", null, null, UUID.randomUUID());

        service.checkout(request);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getBank()).isNull();
        assertThat(saved.getValue().getBankName()).isNull();
    }
}
