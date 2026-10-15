package com.company.erp.expense;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.expense.dto.ExpenseRequest;
import com.company.erp.expense.dto.ExpenseResponse;
import com.company.erp.finance.FinancialReferenceType;
import com.company.erp.finance.FinancialTransactionService;
import com.company.erp.finance.FinancialTransactionType;
import com.company.erp.security.BranchAccessService;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final FinancialTransactionService financialTransactionService;

    @Transactional
    public ExpenseResponse create(ExpenseRequest request) {
        branchAccessService.assertCanWriteToBranch(request.branchId());
        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> ResourceNotFoundException.of("Branch", request.branchId()));
        User creator = currentUserEntity();

        Expense expense = new Expense();
        expense.setBranch(branch);
        expense.setCategory(request.category());
        expense.setAmount(request.amount());
        expense.setDescription(request.description());
        expense.setExpenseDate(request.expenseDate());
        expense.setPaymentMethod(request.paymentMethod());
        expense.setReference(request.reference());
        expense.setCreatedBy(creator);
        expense = expenseRepository.save(expense);

        financialTransactionService.record(branch, FinancialTransactionType.EXPENSE, request.amount(),
                request.expenseDate(), FinancialReferenceType.EXPENSE, expense.getId());

        return ExpenseResponse.from(expense);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> search(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                                 ExpenseCategory category, LocalDate from, LocalDate to,
                                                 Pageable pageable) {
        var branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        var page = expenseRepository.search(branchIds, category, from, to, pageable).map(ExpenseResponse::from);
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Expense", id));
        branchAccessService.resolveReadableBranchId(expense.getBranch().getId());
        return ExpenseResponse.from(expense);
    }

    private User currentUserEntity() {
        return userRepository.findById(branchAccessService.currentUser().getUserId()).orElseThrow();
    }
}
