package com.company.erp.loan;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchType;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.customer.Customer;
import com.company.erp.bank.Bank;
import com.company.erp.bank.BankRepository;
import com.company.erp.bank.BankResolver;
import com.company.erp.loan.dto.RecordLoanPaymentRequest;
import com.company.erp.payment.PaymentMethod;
import com.company.erp.payment.PaymentRepository;
import com.company.erp.sales.Sale;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.UserPrincipal;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock private LoanRepository loanRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private BranchAccessService branchAccessService;
    @Mock private UserRepository userRepository;
    @Mock private BankRepository bankRepository;
    @Mock private com.company.erp.finance.FinancialTransactionService financialTransactionService;

    private LoanService service;

    private Branch boleStore;
    private Customer customer;
    private User manager;

    @BeforeEach
    void setUp() {
        service = new LoanService(loanRepository, paymentRepository, branchAccessService, userRepository,
                financialTransactionService, new BankResolver(bankRepository));

        boleStore = new Branch();
        boleStore.setName("Bole Store");
        boleStore.setType(BranchType.STORE);

        customer = new Customer();
        customer.setName("Walk-in Regular");

        manager = new User();
        manager.setName("Bole Store Manager");
        manager.setRole(Role.STORE_MANAGER);

        UserPrincipal principal = new UserPrincipal(manager);
        lenient().when(branchAccessService.currentUser()).thenReturn(principal);
        lenient().when(userRepository.findById(any())).thenReturn(Optional.of(manager));
        lenient().when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paymentRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void createFromSaleCapturesRemainingBalanceAsOriginalAmount() {
        Sale sale = saleWithRemaining(BigDecimal.valueOf(2000));

        Loan loan = service.createFromSale(sale, null);

        assertThat(loan.getOriginalAmount()).isEqualByComparingTo("2000");
        assertThat(loan.getRemainingAmount()).isEqualByComparingTo("2000");
        assertThat(loan.getPaidAmount()).isEqualByComparingTo("0");
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.OPEN);
        // No explicit due date given -> defaults to 30 days out.
        assertThat(loan.getDueDate()).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    void createFromSaleRejectsAFullyPaidSale() {
        Sale sale = saleWithRemaining(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.createFromSale(sale, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void createFromSaleRejectsASaleWithNoCustomer() {
        Sale sale = new Sale();
        sale.setCustomer(null);
        sale.setBranch(boleStore);
        sale.setRemainingAmount(BigDecimal.valueOf(500));

        assertThatThrownBy(() -> service.createFromSale(sale, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void recordPaymentReducesRemainingBalanceAndFlipsToPartiallyPaid() {
        Loan loan = openLoan(BigDecimal.valueOf(2000));
        when(loanRepository.findById(loan.getId())).thenReturn(Optional.of(loan));

        var result = service.recordPayment(loan.getId(),
                new RecordLoanPaymentRequest(PaymentMethod.CASH, BigDecimal.valueOf(800), null, null, null));

        assertThat(result.remainingAmount()).isEqualByComparingTo("1200");
        assertThat(result.status()).isEqualTo("PARTIALLY_PAID");
    }

    @Test
    void payingTheFullRemainingBalanceMarksTheLoanPaid() {
        Loan loan = openLoan(BigDecimal.valueOf(2000));
        when(loanRepository.findById(loan.getId())).thenReturn(Optional.of(loan));

        var result = service.recordPayment(loan.getId(),
                new RecordLoanPaymentRequest(PaymentMethod.BANK, BigDecimal.valueOf(2000), "Bank of Abyssinia", "REF-1", null));

        assertThat(result.remainingAmount()).isEqualByComparingTo("0");
        assertThat(result.status()).isEqualTo("PAID");
    }

    @Test
    void overpayingALoanIsRejected() {
        Loan loan = openLoan(BigDecimal.valueOf(2000));
        when(loanRepository.findById(loan.getId())).thenReturn(Optional.of(loan));

        var request = new RecordLoanPaymentRequest(PaymentMethod.CASH, BigDecimal.valueOf(2001), null, null, null);

        assertThatThrownBy(() -> service.recordPayment(loan.getId(), request))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void payingAnAlreadyPaidLoanIsRejected() {
        Loan loan = openLoan(BigDecimal.valueOf(500));
        loan.setPaidAmount(BigDecimal.valueOf(500));
        loan.setRemainingAmount(BigDecimal.ZERO);
        loan.setStatus(LoanStatus.PAID);
        when(loanRepository.findById(loan.getId())).thenReturn(Optional.of(loan));

        var request = new RecordLoanPaymentRequest(PaymentMethod.CASH, BigDecimal.valueOf(1), null, null, null);

        assertThatThrownBy(() -> service.recordPayment(loan.getId(), request))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void aLoanPastItsDueDateWithABalanceReportsAsOverdueWithoutMutatingPersistedStatus() {
        Loan loan = openLoan(BigDecimal.valueOf(1000));
        loan.setDueDate(LocalDate.now().minusDays(1));

        var effective = com.company.erp.loan.dto.LoanResponse.effectiveStatus(loan);

        assertThat(effective).isEqualTo(LoanStatus.OVERDUE);
        // The underlying persisted field is untouched - still OPEN.
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.OPEN);
    }

    private Sale saleWithRemaining(BigDecimal remaining) {
        Sale sale = new Sale();
        setId(sale, UUID.randomUUID());
        sale.setSaleNumber("SALE-20260115-1234");
        sale.setBranch(boleStore);
        sale.setCustomer(customer);
        sale.setRemainingAmount(remaining);
        return sale;
    }

    @Test
    void reduceForReturnAppliesTheFullAmountWhenItFitsWithinTheRemainingBalance() {
        Loan loan = openLoan(BigDecimal.valueOf(2000));
        loan.setPaidAmount(BigDecimal.valueOf(500));
        loan.setRemainingAmount(BigDecimal.valueOf(1500));

        BigDecimal applied = service.reduceForReturn(loan, BigDecimal.valueOf(750));

        assertThat(applied).isEqualByComparingTo("750");
        assertThat(loan.getOriginalAmount()).isEqualByComparingTo("1250"); // 2000 - 750
        assertThat(loan.getRemainingAmount()).isEqualByComparingTo("750"); // 1250 - 500 paid
        assertThat(loan.getPaidAmount()).isEqualByComparingTo("500"); // untouched - it was real cash
    }

    @Test
    void reduceForReturnCapsAtWhateverIsCurrentlyStillOwedNeverTheOriginalAmount() {
        // Loan is nearly paid off (100 of 2000 remains) - a much larger
        // return should still only write off the 100 actually still owed,
        // not the full return value.
        Loan loan = openLoan(BigDecimal.valueOf(2000));
        loan.setPaidAmount(BigDecimal.valueOf(1900));
        loan.setRemainingAmount(BigDecimal.valueOf(100));

        BigDecimal applied = service.reduceForReturn(loan, BigDecimal.valueOf(3000));

        assertThat(applied).isEqualByComparingTo("100");
        assertThat(loan.getOriginalAmount()).isEqualByComparingTo("1900"); // 2000 - 100
        assertThat(loan.getRemainingAmount()).isEqualByComparingTo("0");
        assertThat(loan.getPaidAmount()).isEqualByComparingTo("1900"); // chk_loan_paid_lte_original still holds: 1900 <= 1900
    }

    @Test
    void reduceForReturnCanWriteALoanAllTheWayDownToZeroWhenNothingWasEverPaid() {
        // The exact edge case that required relaxing the original_amount
        // DB constraint from > 0 to >= 0 (see V11 migration): a customer
        // returns 100% of a sale they never paid a cent of.
        Loan loan = openLoan(BigDecimal.valueOf(3000));
        loan.setPaidAmount(BigDecimal.ZERO);
        loan.setRemainingAmount(BigDecimal.valueOf(3000));

        BigDecimal applied = service.reduceForReturn(loan, BigDecimal.valueOf(3000));

        assertThat(applied).isEqualByComparingTo("3000");
        assertThat(loan.getOriginalAmount()).isEqualByComparingTo("0");
        assertThat(loan.getRemainingAmount()).isEqualByComparingTo("0");
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.PAID);
    }

    @Test
    void reduceForReturnOnAnAlreadyFullyPaidLoanAppliesNothing() {
        Loan loan = openLoan(BigDecimal.valueOf(500));
        loan.setPaidAmount(BigDecimal.valueOf(500));
        loan.setRemainingAmount(BigDecimal.ZERO);
        loan.setStatus(LoanStatus.PAID);

        BigDecimal applied = service.reduceForReturn(loan, BigDecimal.valueOf(200));

        assertThat(applied).isEqualByComparingTo("0");
        assertThat(loan.getOriginalAmount()).isEqualByComparingTo("500"); // untouched
    }

    private Loan openLoan(BigDecimal amount) {
        Loan loan = new Loan();
        setId(loan, UUID.randomUUID());
        loan.setCustomer(customer);
        loan.setSale(saleWithRemaining(amount));
        loan.setBranch(boleStore);
        loan.setOriginalAmount(amount);
        loan.setPaidAmount(BigDecimal.ZERO);
        loan.setRemainingAmount(amount);
        loan.setStatus(LoanStatus.OPEN);
        loan.setDueDate(LocalDate.now().plusDays(30));
        return loan;
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

    @Test
    void aBankRepaymentIsLinkedToTheChosenRegisteredBank() {
        Loan loan = openLoan(BigDecimal.valueOf(2000));
        when(loanRepository.findById(loan.getId())).thenReturn(Optional.of(loan));
        java.util.UUID bankId = java.util.UUID.randomUUID();
        Bank bank = new Bank();
        bank.setName("Awash Bank");
        bank.setAccountNumber("0132000111222");
        when(bankRepository.findById(bankId)).thenReturn(Optional.of(bank));

        service.recordPayment(loan.getId(),
                new RecordLoanPaymentRequest(PaymentMethod.BANK, BigDecimal.valueOf(500), null, "TX-1", bankId));

        org.mockito.ArgumentCaptor<com.company.erp.payment.Payment> saved =
                org.mockito.ArgumentCaptor.forClass(com.company.erp.payment.Payment.class);
        org.mockito.Mockito.verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getBank()).isSameAs(bank);
        assertThat(saved.getValue().getBankName()).isEqualTo("Awash Bank");
        assertThat(saved.getValue().getAccountReference()).isEqualTo("TX-1");
    }
}
