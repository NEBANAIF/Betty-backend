package com.company.erp.inventory;

import com.company.erp.batch.ProductBatch;
import com.company.erp.batch.dto.BatchResponse;
import com.company.erp.batch.dto.CreateBatchRequest;
import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.ContentAccess;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.user.Permission;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Batches > Add Batch: stock that arrives without a purchase order (goods a
 * store or warehouse imported itself, opening stock, and so on). Like every
 * other stock change it goes through StockMutationService.receiveStock, so
 * the batch, the branch's inventory total and the Stock History row
 * (movement type BATCH_ADDED) are written in one transaction.
 */
@Service
@RequiredArgsConstructor
public class BatchEntryService {

    private static final String DEFAULT_REASON = "Batch added manually";

    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final StockMutationService stockMutationService;
    private final ContentAccess contentAccess;

    @Transactional
    public BatchResponse create(CreateBatchRequest request) {
        // request.branchId() is only a target - this is where it is checked
        // against what the signed-in user may actually write to.
        branchAccessService.assertCanWriteToBranch(request.branchId());

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> ResourceNotFoundException.of("Product", request.productId()));
        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> ResourceNotFoundException.of("Branch", request.branchId()));
        User user = userRepository.findById(branchAccessService.currentUser().getUserId()).orElseThrow();

        LocalDate receivedDate = request.receivedDate() != null ? request.receivedDate() : LocalDate.now();
        String reason = request.note() == null || request.note().isBlank() ? DEFAULT_REASON : request.note().trim();

        ProductBatch batch = stockMutationService.receiveStock(product, branch, request.quantity(),
                request.costPrice(), receivedDate, StockMovementType.BATCH_ADDED, reason, user, null, null);

        BatchResponse response = BatchResponse.from(batch);
        return contentAccess.can(Permission.VIEW_COSTS) ? response : response.withoutCost();
    }
}
