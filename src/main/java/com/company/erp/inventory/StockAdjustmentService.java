package com.company.erp.inventory;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.inventory.dto.StockAdjustmentRequest;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.security.BranchAccessService;
import com.company.erp.stockhistory.StockReferenceType;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class StockAdjustmentService {

    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final StockMutationService stockMutationService;

    @Transactional
    public void adjust(StockAdjustmentRequest request) {
        // Never trust request.branchId() as authorization - it's only a
        // target, and this call is where it gets checked against the
        // authenticated user's actual access.
        branchAccessService.assertCanWriteToBranch(request.branchId());

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> ResourceNotFoundException.of("Product", request.productId()));
        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> ResourceNotFoundException.of("Branch", request.branchId()));
        User user = userRepository.findById(branchAccessService.currentUser().getUserId()).orElseThrow();

        switch (request.direction()) {
            case INCREASE -> {
                if (request.costPrice() == null) {
                    throw new BusinessRuleViolationException(
                            "costPrice is required when increasing stock via adjustment");
                }
                stockMutationService.receiveStock(product, branch, request.quantity(), request.costPrice(),
                        LocalDate.now(), request.movementType(), request.reason(), user,
                        StockReferenceType.MANUAL_ADJUSTMENT, null);
            }
            case DECREASE -> stockMutationService.issueStock(product, branch, request.quantity(),
                    request.movementType(), request.reason(), user,
                    StockReferenceType.MANUAL_ADJUSTMENT, null);
        }
    }
}
