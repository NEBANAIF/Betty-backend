package com.company.erp.purchase;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.purchase.dto.CreatePurchaseOrderItemRequest;
import com.company.erp.purchase.dto.CreatePurchaseOrderRequest;
import com.company.erp.purchase.dto.PurchaseOrderResponse;
import com.company.erp.purchase.dto.ShipmentStageRequest;
import com.company.erp.purchase.dto.UpdateShipmentRequest;
import com.company.erp.branch.BranchType;
import com.company.erp.purchase.dto.ReceivePurchaseItemRequest;
import com.company.erp.purchase.dto.ReceivePurchaseRequest;
import com.company.erp.security.BranchAccessService;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.stockhistory.StockReferenceType;
import com.company.erp.supplier.Supplier;
import com.company.erp.supplier.SupplierRepository;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import com.company.erp.inventory.StockMutationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Purchasing workflow per spec section 17:
 * Supplier -> PurchaseOrder -> Receive Purchase -> automatic ProductBatch
 * creation -> increased branch stock. Receiving NEVER requires a separate
 * manual batch-creation step - receive(...) below is the only path into
 * StockMutationService.receiveStock for purchase-driven stock.
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final StockMutationService stockMutationService;
    private final PurchaseReceiptRepository purchaseReceiptRepository;

    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request) {
        branchAccessService.assertCanWriteToBranch(request.branchId());

        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> ResourceNotFoundException.of("Supplier", request.supplierId()));
        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> ResourceNotFoundException.of("Branch", request.branchId()));
        User creator = currentUserEntity();

        PurchaseOrder po = new PurchaseOrder();
        po.setOrderNumber(generateOrderNumber());
        po.setSupplier(supplier);
        po.setBranch(branch);
        po.setOrderDate(request.orderDate());
        po.setStatus(PurchaseOrderStatus.DRAFT);
        po.setCreatedBy(creator);

        PurchaseType type = request.purchaseType() != null ? request.purchaseType() : PurchaseType.LOCAL;
        po.setPurchaseType(type);
        if (type == PurchaseType.GLOBAL) {
            applyGlobalDetails(po, request, branch);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (CreatePurchaseOrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Product", itemRequest.productId()));

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setProduct(product);
            item.setQuantity(itemRequest.quantity());
            item.setUnitCost(itemRequest.unitCost());
            item.setReceivedQuantity(0);
            po.addItem(item);

            total = total.add(item.getLineTotal());
        }
        po.setTotal(total);
        po.refreshShipmentSummary(LocalDate.now());

        return PurchaseOrderResponse.from(purchaseOrderRepository.save(po));
    }

    /**
     * Replaces the route and shipment details of a global purchase. Only the journey changes here - this never
     * adds stock, however far the goods have travelled.
     */
    @Transactional
    public PurchaseOrderResponse updateShipment(UUID id, UpdateShipmentRequest request) {
        PurchaseOrder po = findOrThrow(id);
        branchAccessService.assertCanWriteToBranch(po.getBranch().getId());
        if (!po.isGlobal()) {
            throw new BusinessRuleViolationException("Only global purchases have a shipment to update");
        }
        if (po.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Cannot update the shipment of a cancelled purchase order");
        }

        if (request.shippingMethod() != null) {
            po.setShippingMethod(request.shippingMethod());
        }
        if (request.shippingCost() != null) {
            po.setShippingCost(request.shippingCost());
        }
        if (request.expectedArrivalDate() != null) {
            if (request.expectedArrivalDate().isBefore(po.getOrderDate())) {
                throw new BusinessRuleViolationException("The expected arrival date cannot be before the order date");
            }
            po.setExpectedArrivalDate(request.expectedArrivalDate());
        }
        String newCurrency = request.currency() == null || request.currency().isBlank()
                ? po.getCurrency() : normaliseCurrency(request.currency());
        BigDecimal newRate = request.exchangeRate() != null ? request.exchangeRate() : po.getExchangeRate();
        boolean moneyChanged = !newCurrency.equals(po.getCurrency()) || newRate.compareTo(po.getExchangeRate()) != 0;
        if (moneyChanged) {
            if (po.isPartiallyReceived()) {
                throw new BusinessRuleViolationException(
                        "The currency and exchange rate cannot change after stock has been received - it was already costed with them");
            }
            po.setCurrency(newCurrency);
            po.setExchangeRate(newRate);
        }

        List<PurchaseShipmentStage> stages = buildStages(request.stages(), po.getOriginCountry(), po.getDestinationCountry());
        po.getStages().clear();
        stages.forEach(po::addStage);
        po.refreshShipmentSummary(LocalDate.now());
        return PurchaseOrderResponse.from(po);
    }

    @Transactional
    public PurchaseOrderResponse submit(UUID id) {
        PurchaseOrder po = findOrThrow(id);
        branchAccessService.assertCanWriteToBranch(po.getBranch().getId());
        requireStatus(po, "submit for approval", PurchaseOrderStatus.DRAFT);
        po.setStatus(PurchaseOrderStatus.PENDING);
        return PurchaseOrderResponse.from(po);
    }

    @Transactional
    public PurchaseOrderResponse approve(UUID id) {
        PurchaseOrder po = findOrThrow(id);
        branchAccessService.assertCanWriteToBranch(po.getBranch().getId());
        requireStatus(po, "approve", PurchaseOrderStatus.PENDING);
        po.setStatus(PurchaseOrderStatus.APPROVED);
        po.setApprovedBy(currentUserEntity());
        return PurchaseOrderResponse.from(po);
    }

    @Transactional
    public PurchaseOrderResponse cancel(UUID id) {
        PurchaseOrder po = findOrThrow(id);
        branchAccessService.assertCanWriteToBranch(po.getBranch().getId());
        if (po.getStatus() == PurchaseOrderStatus.RECEIVED || po.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new BusinessRuleViolationException(
                    "Cannot cancel a purchase order that is already " + po.getStatus());
        }
        if (po.isPartiallyReceived()) {
            throw new BusinessRuleViolationException(
                    "Cannot cancel a purchase order that has already received stock - it must run to completion");
        }
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        return PurchaseOrderResponse.from(po);
    }

    /**
     * Receives some or all of the outstanding quantity on one or more
     * lines. Every unit received creates its own ProductBatch (via
     * StockMutationService.receiveStock) at that line's unit cost, and the
     * branch's Inventory + StockHistory are updated in the same
     * transaction. The PO's status is derived afterward from the items'
     * cumulative receivedQuantity - never set directly by the caller.
     */
    @Transactional
    public PurchaseOrderResponse receive(UUID id, ReceivePurchaseRequest request) {
        PurchaseOrder po = findOrThrow(id);
        branchAccessService.assertCanWriteToBranch(po.getBranch().getId());

        // One delivery can be booked in once only: the screen sends a fresh requestId per dialog.
        if (request.requestId() != null && purchaseReceiptRepository.existsByRequestId(request.requestId())) {
            throw new BusinessRuleViolationException(
                    "This delivery has already been received - the stock was not added a second time");
        }

        if (po.getStatus() != PurchaseOrderStatus.APPROVED
                && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new BusinessRuleViolationException(
                    "Purchase order must be APPROVED before it can be received (current status: "
                            + po.getStatus() + ")");
        }

        LocalDate receivedDate = request.receivedDate() != null ? request.receivedDate() : LocalDate.now();
        User receivedBy = currentUserEntity();

        Map<UUID, PurchaseOrderItem> itemsById = new HashMap<>();
        po.getItems().forEach(i -> itemsById.put(i.getId(), i));

        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.setRequestId(request.requestId());
        receipt.setReceivedDate(receivedDate);
        receipt.setReceivedBy(receivedBy);
        receipt.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());

        for (ReceivePurchaseItemRequest line : request.items()) {
            PurchaseOrderItem item = itemsById.get(line.purchaseOrderItemId());
            if (item == null) {
                throw new BusinessRuleViolationException(
                        "Purchase order item " + line.purchaseOrderItemId() + " does not belong to this order");
            }
            if (line.quantityReceived() > item.getRemainingToReceive()) {
                throw new BusinessRuleViolationException(
                        "Cannot receive " + line.quantityReceived() + " units for product " +
                                item.getProduct().getName() + " - only " + item.getRemainingToReceive() +
                                " remain outstanding on this order");
            }

            // An imported order is priced in a foreign currency; stock is costed in the local one.
            BigDecimal batchCost = po.isGlobal()
                    ? item.getUnitCost().multiply(po.getExchangeRate()).setScale(4, RoundingMode.HALF_UP)
                    : item.getUnitCost();

            stockMutationService.receiveStock(
                    item.getProduct(), po.getBranch(), line.quantityReceived(), batchCost,
                    receivedDate, StockMovementType.PURCHASE,
                    "Received against purchase order " + po.getOrderNumber(),
                    receivedBy, StockReferenceType.PURCHASE_ORDER, po.getId(), item.getId());

            item.setReceivedQuantity(item.getReceivedQuantity() + line.quantityReceived());

            PurchaseReceiptLine receiptLine = new PurchaseReceiptLine();
            receiptLine.setPurchaseOrderItem(item);
            receiptLine.setQuantity(line.quantityReceived());
            receipt.addLine(receiptLine);
        }
        po.addReceipt(receipt);

        po.setStatus(po.isFullyReceived() ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        po.refreshShipmentSummary(LocalDate.now());

        return PurchaseOrderResponse.from(po);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(UUID id) {
        PurchaseOrder po = findOrThrow(id);
        branchAccessService.resolveReadableBranchId(po.getBranch().getId());
        return PurchaseOrderResponse.from(po);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> search(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                                        PurchaseSearchCriteria criteria, Pageable pageable) {
        if (criteria.shipmentStatus() != null && !criteria.shipmentStatus().isBlank()) {
            String wanted = criteria.shipmentStatus().trim().toUpperCase();
            if (!wanted.equals("DELAYED")) {
                try {
                    ShipmentStatus.valueOf(wanted);
                } catch (IllegalArgumentException e) {
                    throw new BusinessRuleViolationException("Unknown shipment status \"" + criteria.shipmentStatus()
                            + "\" (use NOT_SHIPPED, IN_TRANSIT, ARRIVED or DELAYED)");
                }
            }
        }
        if (criteria.arrivalMonth() != null && (criteria.arrivalMonth() < 1 || criteria.arrivalMonth() > 12)) {
            throw new BusinessRuleViolationException("The arrival month must be between 1 and 12");
        }
        var branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        var page = purchaseOrderRepository.search(branchIds, criteria, pageable).map(PurchaseOrderResponse::from);
        return PageResponse.of(page);
    }

    /** Fills in and checks everything a global purchase needs on top of a local one. */
    private void applyGlobalDetails(PurchaseOrder po, CreatePurchaseOrderRequest request, Branch warehouse) {
        if (warehouse.getType() != BranchType.WAREHOUSE) {
            throw new BusinessRuleViolationException(
                    "A global purchase must be received into a warehouse - choose a warehouse as the destination");
        }
        String origin = requireText(request.originCountry(), "origin country");
        String destination = requireText(request.destinationCountry(), "destination country");
        if (origin.equalsIgnoreCase(destination)) {
            throw new BusinessRuleViolationException(
                    "The origin and destination country are the same - choose Local for a purchase inside one country");
        }
        if (request.shippingMethod() == null) {
            throw new BusinessRuleViolationException("Choose a shipping method for a global purchase");
        }
        if (request.currency() == null || request.currency().isBlank()) {
            throw new BusinessRuleViolationException("Enter the purchase currency for a global purchase");
        }
        if (request.expectedArrivalDate() == null) {
            throw new BusinessRuleViolationException("Enter the expected arrival date for a global purchase");
        }
        if (request.expectedArrivalDate().isBefore(request.orderDate())) {
            throw new BusinessRuleViolationException("The expected arrival date cannot be before the order date");
        }

        po.setOriginCountry(origin);
        po.setDestinationCountry(destination);
        po.setShippingMethod(request.shippingMethod());
        po.setCurrency(normaliseCurrency(request.currency()));
        po.setExchangeRate(request.exchangeRate() != null ? request.exchangeRate() : BigDecimal.ONE);
        po.setShippingCost(request.shippingCost() != null ? request.shippingCost() : BigDecimal.ZERO);
        po.setExpectedArrivalDate(request.expectedArrivalDate());

        buildStages(request.stages(), origin, destination).forEach(po::addStage);
    }

    private List<PurchaseShipmentStage> buildStages(List<ShipmentStageRequest> requests, String origin, String destination) {
        return RouteValidator.build(requests, origin, destination, LocalDate.now());
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException("Enter the " + what + " for a global purchase");
        }
        return value.trim();
    }

    private static String normaliseCurrency(String raw) {
        String code = raw.trim().toUpperCase();
        if (!code.matches("[A-Z]{2,10}")) {
            throw new BusinessRuleViolationException("The currency must be a code such as USD, EUR or CNY");
        }
        return code;
    }

    private void requireStatus(PurchaseOrder po, String action, PurchaseOrderStatus required) {
        if (po.getStatus() != required) {
            throw new BusinessRuleViolationException(
                    "Cannot " + action + " a purchase order in status " + po.getStatus() +
                            " (expected " + required + ")");
        }
    }

    private User currentUserEntity() {
        return userRepository.findById(branchAccessService.currentUser().getUserId()).orElseThrow();
    }

    private String generateOrderNumber() {
        String stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String candidate;
        do {
            int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
            candidate = "PO-" + stamp + "-" + suffix;
        } while (purchaseOrderRepository.existsByOrderNumber(candidate));
        return candidate;
    }

    private PurchaseOrder findOrThrow(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("PurchaseOrder", id));
    }
}
