package com.company.erp.payment;

import com.company.erp.bank.Bank;
import com.company.erp.branch.Branch;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.loan.LoanRepository;
import com.company.erp.sales.Sale;
import com.company.erp.sales.SaleRepository;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.ContentAccess;
import com.company.erp.user.Permission;
import com.company.erp.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private BranchAccessService branchAccessService;
    @Mock private SaleRepository saleRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private ContentAccess contentAccess;

    private PaymentQueryService service;
    private Payment bankPayment;
    private UUID saleId;

    @BeforeEach
    void setUp() {
        service = new PaymentQueryService(paymentRepository, branchAccessService, saleRepository, loanRepository, contentAccess);

        Branch branch = new Branch();
        branch.setName("Bole Store");
        User cashier = new User();
        cashier.setName("Bole Cashier");
        Bank bank = new Bank();
        bank.setName("Awash Bank");
        bank.setAccountNumber("0132000111222");

        saleId = UUID.randomUUID();
        bankPayment = new Payment();
        bankPayment.setMethod(PaymentMethod.BANK);
        bankPayment.setAmount(BigDecimal.valueOf(3000));
        bankPayment.setBranch(branch);
        bankPayment.setReferenceType(PaymentReferenceType.SALE);
        bankPayment.setReferenceId(saleId);
        bankPayment.setBank(bank);
        bankPayment.setBankName("Awash Bank");
        bankPayment.setAccountReference("TX-1");
        bankPayment.setReceivedBy(cashier);

        Sale sale = new Sale();
        sale.setSaleNumber("S-000123");
        try {
            var field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(sale, saleId);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        when(branchAccessService.resolveReadableBranchIds(any(), any())).thenReturn(List.of(UUID.randomUUID()));
        when(paymentRepository.search(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(bankPayment), PageRequest.of(0, 20), 1));
        when(saleRepository.findAllById(any())).thenReturn(List.of(sale));
    }

    @Test
    void theTableShowsTheSaleNumberAndTheBankAccountForARoleThatMaySeeIt() {
        when(contentAccess.can(Permission.BANK_VIEW)).thenReturn(true);

        var page = service.search(BranchAccessService.BranchScope.ALL, null, null, null, null, null, null, null, PageRequest.of(0, 20));

        var row = page.content().get(0);
        assertThat(row.referenceNumber()).isEqualTo("S-000123");
        assertThat(row.branchName()).isEqualTo("Bole Store");
        assertThat(row.bankName()).isEqualTo("Awash Bank");
        assertThat(row.bankAccountNumber()).isEqualTo("0132000111222");
        assertThat(row.accountReference()).isEqualTo("TX-1");
        assertThat(row.receivedByName()).isEqualTo("Bole Cashier");
    }

    @Test
    void theBankAccountNumberIsLeftOutForARoleWithoutBankView() {
        when(contentAccess.can(Permission.BANK_VIEW)).thenReturn(false);

        var page = service.search(BranchAccessService.BranchScope.ALL, null, null, null, null, null, null, null, PageRequest.of(0, 20));

        var row = page.content().get(0);
        assertThat(row.bankName()).isEqualTo("Awash Bank");
        assertThat(row.bankAccountNumber()).isNull();
        assertThat(row.referenceNumber()).isEqualTo("S-000123");
    }
}
