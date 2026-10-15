package com.company.erp.dashboard;

import com.company.erp.dashboard.dto.DashboardSummaryResponse;
import com.company.erp.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DASHBOARD_VIEW is held by every role (see RolePermissionRegistry) -
 * branch users automatically see only their own branch, since
 * branchAccessService.resolveReadableBranchIds collapses any requested
 * scope down to their assigned branch regardless of what's passed here.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardContentFilter contentFilter;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public DashboardSummaryResponse getSummary(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        // Default to "today" when no range is given - a dashboard opened
        // with no filters should show today's activity, not an error.
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo;
        // Leave out whatever the caller's role may not see (costs, profit, inventory value, panels...).
        return contentFilter.apply(dashboardService.getSummary(scope, branchId, effectiveFrom, effectiveTo));
    }
}
