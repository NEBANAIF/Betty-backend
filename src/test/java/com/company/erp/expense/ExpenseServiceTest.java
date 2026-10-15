package com.company.erp.expense;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.expense.dto.ExpenseRequest;
import com.company.erp.finance.FinancialReferenceType;
import com.company.erp.finance.FinancialTransactionService;
import com.company.erp.finance.FinancialTransactionType;
import com.company.erp.payment.PaymentMethod;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchAccessService branchAccessService;
    @Mock private FinancialTransactionService financialTransactionService;

    private ExpenseService service;

    private Branch boleStore;
    private User manager;
    private UUID branchId;

    @BeforeEach
    void setUp() {
        service = new ExpenseService(expenseRepository, branchRepository, userRepository,
                branchAccessService, financialTransactionService);

        branchId = UUID.randomUUID();
        boleStore = new Branch();
        boleStore.setName("Bole Store");
        setId(boleStore, branchId);

        manager = new User();
        manager.setName("Bole Store Manager");
        manager.setRole(Role.STORE_MANAGER);
        setId(manager, UUID.randomUUID());

        UserPrincipal principal = new UserPrincipal(manager);
        lenient().when(branchAccessService.currentUser()).thenReturn(principal);
        lenient().when(userRepository.findById(any())).thenReturn(Optional.of(manager));
        lenient().when(branchRepository.findById(branchId)).thenReturn(Optional.of(boleStore));
        lenient().when(expenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void creatingAnExpensePostsAnExpenseLedgerEntryForTheSameAmountAndDate() {
        var request = new ExpenseRequest(branchId, ExpenseCategory.RENT, BigDecimal.valueOf(5000),
                "September rent", LocalDate.of(2026, 9, 1), PaymentMethod.BANK, "REF-100");

        var result = service.create(request);

        assertThat(result.amount()).isEqualByComparingTo("5000");
        assertThat(result.category()).isEqualTo("RENT");

        verify(financialTransactionService).record(eq(boleStore), eq(FinancialTransactionType.EXPENSE),
                eq(BigDecimal.valueOf(5000)), eq(LocalDate.of(2026, 9, 1)),
                eq(FinancialReferenceType.EXPENSE), any());
    }

    @Test
    void branchAccessIsCheckedBeforeAnythingElse() {
        var request = new ExpenseRequest(branchId, ExpenseCategory.OTHER, BigDecimal.TEN,
                "Misc", LocalDate.now(), PaymentMethod.CASH, null);

        service.create(request);

        verify(branchAccessService).assertCanWriteToBranch(branchId);
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
