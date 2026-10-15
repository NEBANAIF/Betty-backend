package com.company.erp.branch;

import com.company.erp.branch.dto.AssignManagerRequest;
import com.company.erp.branch.dto.BranchOptionResponse;
import com.company.erp.branch.dto.BranchResponse;
import com.company.erp.branch.dto.CreateBranchRequest;
import com.company.erp.branch.dto.SetActiveRequest;
import com.company.erp.branch.dto.UpdateBranchRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    /**
     * Powers the branch selector. Every authenticated user can call this -
     * what they get back is scoped server-side by BranchService, not by
     * this controller trusting the `type` filter.
     */
    @GetMapping
    public List<BranchResponse> list(@RequestParam(required = false) BranchType type) {
        return branchService.listAccessibleBranches(type);
    }

    /**
     * Every active branch as a bare dropdown option, for choosing the source and
     * destination of a stock transfer. Needs a transfer permission; returns all
     * branches (not only the caller's own) but no branch details.
     */
    @GetMapping("/transfer-targets")
    @PreAuthorize("hasAnyAuthority('TRANSFER_CREATE', 'TRANSFER_APPROVE', 'TRANSFER_RECEIVE')")
    public List<BranchOptionResponse> transferTargets() {
        return branchService.listTransferBranches();
    }

    @GetMapping("/{id}")
    public BranchResponse get(@PathVariable UUID id) {
        return branchService.getBranch(id);
    }

    @GetMapping("/{id}/stats")
    public com.company.erp.branch.dto.BranchStatsResponse stats(@PathVariable UUID id) {
        return branchService.getStats(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public BranchResponse create(@Valid @RequestBody CreateBranchRequest request) {
        return branchService.createBranch(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public BranchResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateBranchRequest request) {
        return branchService.updateBranch(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public BranchResponse setActive(@PathVariable UUID id, @RequestBody SetActiveRequest request) {
        return branchService.setActive(id, request.active());
    }

    @PatchMapping("/{id}/manager")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public BranchResponse assignManager(@PathVariable UUID id, @Valid @RequestBody AssignManagerRequest request) {
        return branchService.assignManager(id, request);
    }
}
