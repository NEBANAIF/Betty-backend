package com.company.erp.supplier;

import com.company.erp.common.exception.DuplicateResourceException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.purchase.PurchaseOrderRepository;
import com.company.erp.returns.supplier.SupplierReturnAllocationRepository;
import com.company.erp.supplier.dto.SupplierBalanceResponse;
import com.company.erp.supplier.dto.SupplierRequest;
import com.company.erp.supplier.dto.SupplierResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierReturnAllocationRepository supplierReturnAllocationRepository;

    @Transactional(readOnly = true)
    public List<SupplierResponse> list(boolean includeInactive) {
        var suppliers = includeInactive ? supplierRepository.findAll() : supplierRepository.findByActiveTrue();
        return suppliers.stream().map(SupplierResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(UUID id) {
        return SupplierResponse.from(findOrThrow(id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        if (supplierRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A supplier named '" + request.name() + "' already exists");
        }
        Supplier supplier = new Supplier();
        applyRequest(supplier, request);
        supplier.setActive(true);
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse update(UUID id, SupplierRequest request) {
        Supplier supplier = findOrThrow(id);
        if (!supplier.getName().equalsIgnoreCase(request.name())
                && supplierRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A supplier named '" + request.name() + "' already exists");
        }
        applyRequest(supplier, request);
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public SupplierResponse setActive(UUID id, boolean active) {
        Supplier supplier = findOrThrow(id);
        supplier.setActive(active);
        return SupplierResponse.from(supplier);
    }

    /**
     * totalPurchased is the real value of everything physically RECEIVED
     * so far (receivedQuantity * unitCost per line, summed), MINUS the
     * value of anything since sent back (Phase 9's "adjust supplier
     * balance" - SupplierReturnAllocationRepository.sumReturnedValueForSupplier)
     * - not the ordered total, since an order that's only half-received
     * hasn't created a payable obligation for the unreceived half, and a
     * returned unit no longer represents one either. There is no stored,
     * mutable "balance" field anywhere - both figures are summed fresh
     * from PurchaseOrderItem/SupplierReturnAllocation on every read, the
     * same derived-not-stored approach used for COGS, gross profit, and
     * customer loan balances.
     * <p>
     * totalPaid/outstandingBalance stay at their real values once Payments
     * (Phase 16) exists; until then outstanding == net purchased since
     * nothing has been recorded as paid.
     */
    @Transactional(readOnly = true)
    public SupplierBalanceResponse getBalance(UUID id) {
        findOrThrow(id);
        BigDecimal received = purchaseOrderRepository.sumReceivedValueForSupplier(id);
        BigDecimal returned = supplierReturnAllocationRepository.sumReturnedValueForSupplier(id);
        BigDecimal totalPurchased = received.subtract(returned);
        BigDecimal totalPaid = BigDecimal.ZERO;
        return new SupplierBalanceResponse(id, totalPurchased, totalPaid, totalPurchased.subtract(totalPaid));
    }

    private void applyRequest(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.name());
        supplier.setPhone(request.phone());
        supplier.setEmail(request.email());
        supplier.setAddress(request.address());
        supplier.setTaxNumber(request.taxNumber());
    }

    private Supplier findOrThrow(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Supplier", id));
    }
}
