package com.company.erp.branch;

import com.company.erp.branch.dto.AssignManagerRequest;
import com.company.erp.branch.dto.BranchOptionResponse;
import com.company.erp.branch.dto.BranchResponse;
import com.company.erp.branch.dto.BranchStatsResponse;
import com.company.erp.branch.dto.CreateBranchRequest;
import com.company.erp.branch.dto.UpdateBranchRequest;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.DuplicateResourceException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.expense.ExpenseRepository;
import com.company.erp.inventory.Inventory;
import com.company.erp.inventory.InventoryRepository;
import com.company.erp.productbranch.ProductBranchAvailabilityService;
import com.company.erp.purchase.PurchaseOrderRepository;
import com.company.erp.sales.SaleBatchAllocationRepository;
import com.company.erp.sales.SaleRepository;
import com.company.erp.sales.SaleStatus;
import com.company.erp.security.BranchAccessService;
import com.company.erp.user.Role;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final InventoryRepository inventoryRepository;
    private final ProductBranchAvailabilityService productBranchAvailabilityService;
    private final ProductBatchRepository productBatchRepository;
    private final SaleRepository saleRepository;
    private final SaleBatchAllocationRepository saleBatchAllocationRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ExpenseRepository expenseRepository;


    /**
     * Every active store and warehouse, in name order, as bare id/name/code/type
     * options. Unlike listAccessibleBranches this is NOT limited to the caller's
     * own branch: a transfer runs between two branches, and someone at one of
     * them has to be able to name the other. Only the four dropdown fields are
     * returned; the transfer itself is still checked against the caller's own
     * branch when it is created (see BranchAccessService).
     */
    @Transactional(readOnly = true)
    public List<BranchOptionResponse> listTransferBranches() {
        return branchRepository.findByActiveTrue().stream()
                .sorted(java.util.Comparator.comparing(Branch::getName, String.CASE_INSENSITIVE_ORDER))
                .map(BranchOptionResponse::from)
                .toList();
    }

    /**
     * Branch selector / list endpoint. SUPER_ADMIN gets everything
     * (optionally filtered by type); every other role always gets exactly
     * their own single branch, regardless of what typeFilter they pass -
     * the frontend selector for non-admins should just render a static
     * label, but even if it sends a filter, the backend never lets it
     * widen access.
     */
    @Transactional(readOnly = true)
    public List<BranchResponse> listAccessibleBranches(BranchType typeFilter) {
        var user = branchAccessService.currentUser();

        if (!user.isSuperAdmin()) {
            return branchRepository.findById(user.getBranchId())
                    .map(BranchResponse::from)
                    .map(List::of)
                    .orElse(List.of());
        }

        List<Branch> branches = typeFilter != null
                ? branchRepository.findByType(typeFilter)
                : branchRepository.findAll();

        return branches.stream().map(BranchResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BranchResponse getBranch(UUID branchId) {
        branchAccessService.resolveReadableBranchId(branchId);
        return branchRepository.findById(branchId)
                .map(BranchResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Branch", branchId));
    }

    @Transactional
    public BranchResponse createBranch(CreateBranchRequest request) {
        if (branchRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A branch named '" + request.name() + "' already exists");
        }
        branchRepository.findByCodeIgnoreCase(request.code()).ifPresent(b -> {
            throw new DuplicateResourceException("Branch code '" + request.code() + "' is already in use");
        });

        Branch branch = new Branch();
        branch.setName(request.name());
        branch.setCode(request.code());
        branch.setType(request.type());
        branch.setAddress(request.address());
        branch.setPhone(request.phone());
        branch.setEmail(request.email());
        branch.setActive(true);

        Branch saved = branchRepository.save(branch);
        // Starts fully checked: every existing product is allowed here too (see
        // ProductBranchAvailability's javadoc). Someone unchecks it on this branch's checklist afterwards.
        productBranchAvailabilityService.seedForNewBranch(saved.getId());
        return BranchResponse.from(saved);
    }

    @Transactional
    public BranchResponse updateBranch(UUID branchId, UpdateBranchRequest request) {
        Branch branch = findOrThrow(branchId);

        if (!branch.getName().equalsIgnoreCase(request.name())
                && branchRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A branch named '" + request.name() + "' already exists");
        }

        branch.setName(request.name());
        branch.setAddress(request.address());
        branch.setPhone(request.phone());
        branch.setEmail(request.email());

        return BranchResponse.from(branch);
    }

    @Transactional
    public BranchResponse setActive(UUID branchId, boolean active) {
        Branch branch = findOrThrow(branchId);
        branch.setActive(active);
        return BranchResponse.from(branch);
    }

    /**
     * A branch's manager must already be a user assigned to that branch,
     * with a manager-level role matching the branch type. This keeps
     * "manager" meaningful (it's not just a label - STORE_MANAGER /
     * WAREHOUSE_MANAGER permissions are what the role actually grants) and
     * prevents assigning, say, a warehouse manager as a store's manager.
     */
    @Transactional
    public BranchResponse assignManager(UUID branchId, AssignManagerRequest request) {
        Branch branch = findOrThrow(branchId);

        if (request.userId() == null) {
            branch.setManager(null);
            return BranchResponse.from(branch);
        }

        User manager = userRepository.findById(request.userId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", request.userId()));

        boolean belongsToBranch = manager.getBranch() != null && manager.getBranch().getId().equals(branchId);
        boolean correctRole = (branch.getType() == BranchType.STORE && manager.getRole() == Role.STORE_MANAGER)
                || (branch.getType() == BranchType.WAREHOUSE && manager.getRole() == Role.WAREHOUSE_MANAGER);

        if (!belongsToBranch || !correctRole) {
            throw new BusinessRuleViolationException(
                    "The manager must already be assigned to this branch with the matching manager role");
        }

        branch.setManager(manager);
        return BranchResponse.from(branch);
    }

    /**
     * Branch statistics for the Branch Management detail view (spec
     * section 28). Real figures depend on Inventory (Phase 4), Sales/
     * Purchases (Phases 5-6), and Finance (Phase 10) - until those land,
     * this returns a zeroed shape so the frontend can build the screen
     * against the final contract now rather than waiting.
     */
    /**
     * Real branch statistics (spec section 28), computed live from
     * Inventory/ProductBatch/Sale/PurchaseOrder/Expense - not stored or
     * cached, so they're never stale. "All time" (Instant.EPOCH / earliest
     * possible LocalDate through now) rather than a date-scoped P&L like
     * FinanceReportService, since this is a branch identity summary
     * ("how much has this branch ever done"), not a reporting-period
     * query - the Finance and Reports pages are where date ranges apply.
     */
    @Transactional(readOnly = true)
    public BranchStatsResponse getStats(UUID branchId) {
        branchAccessService.resolveReadableBranchId(branchId);
        if (!branchRepository.existsById(branchId)) {
            throw ResourceNotFoundException.of("Branch", branchId);
        }

        List<UUID> branchIds = List.of(branchId);
        Instant epoch = Instant.EPOCH;
        Instant now = Instant.now();
        LocalDate earliestDate = LocalDate.of(2000, 1, 1);
        LocalDate today = LocalDate.now();

        long totalStockUnits = inventoryRepository.findByBranchId(branchId).stream()
                .mapToLong(Inventory::getQuantity).sum();
        BigDecimal inventoryValue = productBatchRepository.sumInventoryValueForBranch(branchId);

        long totalSalesCount = saleRepository.countByBranchIdAndStatus(branchId, SaleStatus.COMPLETED);
        BigDecimal totalRevenue = saleRepository.sumRevenueForBranchesInRange(
                branchIds, SaleStatus.COMPLETED, epoch, now);
        BigDecimal cogs = saleBatchAllocationRepository.sumCogsForBranchesInRange(
                branchIds, SaleStatus.COMPLETED, epoch, now);

        long totalPurchasesCount = purchaseOrderRepository.countByBranchId(branchId);
        BigDecimal totalExpenses = expenseRepository.sumForBranchesInRange(branchIds, earliestDate, today);

        BigDecimal grossProfit = totalRevenue.subtract(cogs);

        return new BranchStatsResponse(branchId, totalStockUnits, inventoryValue,
                totalSalesCount, totalRevenue, totalPurchasesCount, totalExpenses, grossProfit);
    }

    private Branch findOrThrow(UUID branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> ResourceNotFoundException.of("Branch", branchId));
    }
}
