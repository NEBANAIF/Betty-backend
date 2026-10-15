package com.company.erp.inventory;

import com.company.erp.inventory.dto.CostCorrectionRequest;
import com.company.erp.inventory.dto.ProductCostsResponse;
import com.company.erp.security.ContentAccess;
import com.company.erp.user.Permission;
import com.company.erp.batch.BatchCostCorrectionService;
import com.company.erp.inventory.dto.StockOverviewResponse;
import com.company.erp.security.BranchAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final StockOverviewService stockOverviewService;
    private final ProductCostService productCostService;
    private final BatchCostCorrectionService batchCostCorrectionService;
    private final BranchAccessService branchAccessService;
    private final ContentAccess contentAccess;

    /**
     * Powers Inventory > Stock Overview (spec section 10). `scope` mirrors
     * the branch selector: ALL / STORES / WAREHOUSES / SPECIFIC. Non-admin
     * callers always collapse to their own single branch regardless of
     * what's passed here - enforced in BranchAccessService, not here.
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public StockOverviewResponse overview(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId) {
        return stockOverviewService.getOverview(scope, branchId);
    }

    /**
     * Per-product stock quantity, oldest/latest batch cost and stock value
     * for the selected branch scope, plus the total inventory value.
     * Cost figures are sensitive: batch costs need VIEW_COSTS and stock value / the total need
     * VIEW_INVENTORY_VALUE. Whichever the caller lacks is left out of the response.
     */
    @GetMapping("/costs")
    @PreAuthorize("hasAnyAuthority('VIEW_COSTS', 'VIEW_INVENTORY_VALUE')")
    public ProductCostsResponse costs(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId) {
        return productCostService.getCosts(scope, branchId)
                .limitedTo(contentAccess.can(Permission.VIEW_COSTS), contentAccess.can(Permission.VIEW_INVENTORY_VALUE));
    }

    /**
     * Corrects the cost recorded on a product's oldest in-stock batch - the
     * "someone with stock-adjust access corrects it later" half of the
     * Settings > Inventory > Lock cost price toggle (see SystemSettings and
     * BatchCostCorrectionService). `scope`/`branchId` mirror every other
     * branch-scoped endpoint here: a SUPER_ADMIN can target any reachable
     * branch or scope, everyone else always collapses to their own branch.
     */
    @PatchMapping("/products/{productId}/cost")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void correctCost(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @Valid @RequestBody CostCorrectionRequest request) {
        batchCostCorrectionService.correctOldestCost(
                productId, branchAccessService.resolveReadableBranchIds(scope, branchId), request.costPrice());
    }
}
