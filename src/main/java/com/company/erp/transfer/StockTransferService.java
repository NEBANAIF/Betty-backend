package com.company.erp.transfer;

import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.security.BranchAccessService;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.stockhistory.StockReferenceType;
import com.company.erp.transfer.dto.CreateTransferRequest;
import com.company.erp.transfer.dto.RejectTransferRequest;
import com.company.erp.transfer.dto.TransferResponse;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The full transfer lifecycle (spec section 19). Stock moves exactly
 * twice across the whole lifecycle:
 *
 *  - dispatch() (APPROVED -> IN_TRANSIT): FIFO-consumes the source
 *    branch's stock via StockMutationService.issueStock - the exact same
 *    mechanism a sale uses, just issued against a transfer reference
 *    instead of a sale. Each batch actually consumed is recorded as a
 *    StockTransferAllocation, preserving its cost.
 *  - receive() (IN_TRANSIT -> RECEIVED): for every allocation recorded at
 *    dispatch time, StockMutationService.receiveStock creates a new batch
 *    at the destination branch at that EXACT preserved cost - never a
 *    single averaged cost, never today's cost.
 *
 * PENDING and APPROVED never touch stock at all - only a status/who/when
 * change. REJECTED and CANCELLED are only reachable before dispatch(), by
 * design: once stock has left the source branch there is no undo path in
 * this phase, so the transfer must run to RECEIVED.
 */
@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final StockMutationService stockMutationService;
    private final ProductBatchRepository productBatchRepository;

    @Transactional
    public TransferResponse create(CreateTransferRequest request) {
        if (request.sourceBranchId().equals(request.destinationBranchId())) {
            throw new BusinessRuleViolationException("Source and destination branch must be different");
        }
        // The requester only needs to be acting for one side of the
        // transfer (the store asking for stock, or the warehouse offering
        // it) - never required to already have access to both.
        branchAccessService.assertCanWriteToEitherBranch(request.sourceBranchId(), request.destinationBranchId());

        Branch source = findBranchOrThrow(request.sourceBranchId());
        Branch destination = findBranchOrThrow(request.destinationBranchId());
        User requester = currentUserEntity();

        StockTransfer transfer = new StockTransfer();
        transfer.setTransferNumber(generateTransferNumber());
        transfer.setSourceBranch(source);
        transfer.setDestinationBranch(destination);
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setRequestedBy(requester);
        transfer.setRequestedAt(Instant.now());

        request.items().forEach(itemRequest -> {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Product", itemRequest.productId()));
            StockTransferItem item = new StockTransferItem();
            item.setProduct(product);
            item.setQuantity(itemRequest.quantity());
            transfer.addItem(item);
        });

        return TransferResponse.from(stockTransferRepository.save(transfer));
    }

    /** Authorization to release stock rests with the SOURCE branch. */
    @Transactional
    public TransferResponse approve(UUID transferId) {
        StockTransfer transfer = findOrThrow(transferId);
        requireStatus(transfer, TransferStatus.PENDING);
        branchAccessService.assertCanWriteToBranch(transfer.getSourceBranch().getId());

        transfer.setStatus(TransferStatus.APPROVED);
        transfer.setApprovedBy(currentUserEntity());
        transfer.setApprovedAt(Instant.now());
        return TransferResponse.from(transfer);
    }

    @Transactional
    public TransferResponse reject(UUID transferId, RejectTransferRequest request) {
        StockTransfer transfer = findOrThrow(transferId);
        if (transfer.getStatus() != TransferStatus.PENDING && transfer.getStatus() != TransferStatus.APPROVED) {
            throw new BusinessRuleViolationException(
                    "Only a PENDING or APPROVED transfer can be rejected - stock has already moved");
        }
        branchAccessService.assertCanWriteToBranch(transfer.getSourceBranch().getId());

        transfer.setStatus(TransferStatus.REJECTED);
        transfer.setRejectionReason(request.reason());
        return TransferResponse.from(transfer);
    }

    /** The requester may withdraw their own request before it's actioned either way. */
    @Transactional
    public TransferResponse cancel(UUID transferId) {
        StockTransfer transfer = findOrThrow(transferId);
        requireStatus(transfer, TransferStatus.PENDING);
        branchAccessService.assertCanWriteToEitherBranch(
                transfer.getSourceBranch().getId(), transfer.getDestinationBranch().getId());

        transfer.setStatus(TransferStatus.CANCELLED);
        return TransferResponse.from(transfer);
    }

    /**
     * The moment stock actually leaves the source branch. FIFO-consumes
     * each item's requested quantity and records exactly which batches (and
     * costs) fulfilled it, so receive() can preserve that cost downstream.
     */
    @Transactional
    public TransferResponse dispatch(UUID transferId) {
        StockTransfer transfer = findOrThrow(transferId);
        requireStatus(transfer, TransferStatus.APPROVED);
        branchAccessService.assertCanWriteToBranch(transfer.getSourceBranch().getId());

        User dispatcher = currentUserEntity();

        for (StockTransferItem item : transfer.getItems()) {
            var consumptions = stockMutationService.issueStock(
                    item.getProduct(), transfer.getSourceBranch(), item.getQuantity(),
                    StockMovementType.TRANSFER_OUT, "Transfer " + transfer.getTransferNumber() + " dispatched",
                    dispatcher, StockReferenceType.TRANSFER, transfer.getId());

            for (var consumption : consumptions) {
                StockTransferAllocation allocation = new StockTransferAllocation();
                allocation.setSourceBatch(productBatchRepository.getReferenceById(consumption.batchId()));
                allocation.setQuantityAllocated(consumption.quantityConsumed());
                allocation.setUnitCost(consumption.unitCost());
                item.addAllocation(allocation);
            }
        }

        transfer.setStatus(TransferStatus.IN_TRANSIT);
        transfer.setDispatchedAt(Instant.now());
        return TransferResponse.from(transfer);
    }

    /**
     * The moment stock actually lands at the destination branch. Creates
     * one new destination batch per allocation line recorded at dispatch
     * time, each at that line's exact preserved cost - deliberately not
     * merged into a single batch at an averaged cost, so cost lineage
     * stays traceable all the way back to the original purchase receipt.
     */
    @Transactional
    public TransferResponse receive(UUID transferId) {
        StockTransfer transfer = findOrThrow(transferId);
        requireStatus(transfer, TransferStatus.IN_TRANSIT);
        branchAccessService.assertCanWriteToBranch(transfer.getDestinationBranch().getId());

        User receiver = currentUserEntity();

        for (StockTransferItem item : transfer.getItems()) {
            for (StockTransferAllocation allocation : item.getAllocations()) {
                stockMutationService.receiveStock(
                        item.getProduct(), transfer.getDestinationBranch(), allocation.getQuantityAllocated(),
                        allocation.getUnitCost(), LocalDate.now(), StockMovementType.TRANSFER_IN,
                        "Transfer " + transfer.getTransferNumber() + " received", receiver,
                        StockReferenceType.TRANSFER, transfer.getId());
            }
        }

        transfer.setStatus(TransferStatus.RECEIVED);
        transfer.setReceivedBy(receiver);
        transfer.setReceivedAt(Instant.now());
        return TransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public com.company.erp.common.dto.PageResponse<com.company.erp.transfer.dto.TransferSummaryResponse> search(
            BranchAccessService.BranchScope scope, UUID specificBranchId, TransferStatus status, String search,
            org.springframework.data.domain.Pageable pageable) {
        return search(scope, specificBranchId, status, search, null, pageable);
    }

    @Transactional(readOnly = true)
    public com.company.erp.common.dto.PageResponse<com.company.erp.transfer.dto.TransferSummaryResponse> search(
            BranchAccessService.BranchScope scope, UUID specificBranchId, TransferStatus status, String search,
            UUID productId, org.springframework.data.domain.Pageable pageable) {
        return search(scope, specificBranchId, status, search, productId, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public com.company.erp.common.dto.PageResponse<com.company.erp.transfer.dto.TransferSummaryResponse> search(
            BranchAccessService.BranchScope scope, UUID specificBranchId, TransferStatus status, String search,
            UUID productId, java.time.Instant from, java.time.Instant to,
            org.springframework.data.domain.Pageable pageable) {
        var branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        var found = stockTransferRepository.search(branchIds, status, search, productId, from, to, pageable);
        return com.company.erp.common.dto.PageResponse.of(
                found.map(com.company.erp.transfer.dto.TransferSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public TransferResponse get(UUID id) {
        StockTransfer transfer = findOrThrow(id);
        if (!branchAccessService.canReadEitherBranch(
                transfer.getSourceBranch().getId(), transfer.getDestinationBranch().getId())) {
            throw ForbiddenException.branchAccessDenied(transfer.getSourceBranch().getId());
        }
        return TransferResponse.from(transfer);
    }

    private void requireStatus(StockTransfer transfer, TransferStatus expected) {
        if (transfer.getStatus() != expected) {
            throw new BusinessRuleViolationException(
                    "Transfer " + transfer.getTransferNumber() + " is " + transfer.getStatus() +
                            ", expected " + expected);
        }
    }

    private User currentUserEntity() {
        return userRepository.findById(branchAccessService.currentUser().getUserId()).orElseThrow();
    }

    private Branch findBranchOrThrow(UUID branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> ResourceNotFoundException.of("Branch", branchId));
    }

    private StockTransfer findOrThrow(UUID id) {
        return stockTransferRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Transfer", id));
    }

    private String generateTransferNumber() {
        String stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String candidate;
        do {
            int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
            candidate = "TRF-" + stamp + "-" + suffix;
        } while (stockTransferRepository.existsByTransferNumber(candidate));
        return candidate;
    }
}
