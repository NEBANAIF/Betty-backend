package com.company.erp.security;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.branch.BranchType;
import com.company.erp.common.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * THE authorization chokepoint for every branch-sensitive operation.
 *
 * Every service method that reads or writes branch-scoped data
 * (Inventory, Sale, Purchase, Expense, Transfer, StockHistory, ...) must
 * call one of the methods below instead of trusting a branchId that
 * arrived from the frontend. This is what turns
 * "GET /api/sales?branchId=someoneElsesBranch" into a 403 rather than a
 * data leak, and it's the only place that logic lives - no controller or
 * repository query should re-implement it.
 *
 * Rules encoded here:
 * - SUPER_ADMIN can access any branch, and "no branchId filter" means "all
 *   branches" for them.
 * - Any other role can access exactly one branch: their assigned branch.
 *   A non-admin user with no assigned branch is a data-integrity bug (the
 *   invariant is enforced at user-creation time in UserService) and is
 *   treated as "no access" here, not as "all access".
 */
@Service
@RequiredArgsConstructor
public class BranchAccessService {

    private final BranchRepository branchRepository;

    public UserPrincipal currentUser() {
        return (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /**
     * Resolves the effective branchId to use for a read query, given an
     * optional branchId the caller (frontend) requested as a filter.
     *
     * - SUPER_ADMIN passing null -> Optional.empty() (meaning "no filter,
     *   return all branches" to the caller).
     * - SUPER_ADMIN passing a branchId -> that branchId, if it exists.
     * - Non-admin passing null -> their own branchId.
     * - Non-admin passing anything other than their own branchId -> 403.
     */
    public Optional<UUID> resolveReadableBranchId(UUID requestedBranchId) {
        UserPrincipal user = currentUser();

        if (user.isSuperAdmin()) {
            if (requestedBranchId == null) {
                return Optional.empty();
            }
            assertBranchExists(requestedBranchId);
            return Optional.of(requestedBranchId);
        }

        UUID ownBranchId = requireOwnBranch(user);
        if (requestedBranchId != null && !requestedBranchId.equals(ownBranchId)) {
            throw ForbiddenException.branchAccessDenied(requestedBranchId);
        }
        return Optional.of(ownBranchId);
    }

    /**
     * Resolves the set of branch IDs a SUPER_ADMIN's "All Stores" / "All
     * Warehouses" / "All Branches" dashboard-and-report filters expand to.
     * Non-admins always collapse to their single branch regardless of the
     * requested scope.
     */
    public List<UUID> resolveReadableBranchIds(BranchScope scope, UUID specificBranchId) {
        UserPrincipal user = currentUser();

        if (!user.isSuperAdmin()) {
            return List.of(requireOwnBranch(user));
        }

        return switch (scope) {
            case SPECIFIC -> {
                if (specificBranchId == null) {
                    throw new ForbiddenException("A specific branch must be provided for this scope");
                }
                assertBranchExists(specificBranchId);
                yield List.of(specificBranchId);
            }
            case STORES -> branchRepository.findByType(BranchType.STORE).stream().map(Branch::getId).toList();
            case WAREHOUSES -> branchRepository.findByType(BranchType.WAREHOUSE).stream().map(Branch::getId).toList();
            case ALL -> branchRepository.findAll().stream().map(Branch::getId).toList();
        };
    }

    /**
     * Asserts the current user may WRITE to the given branch. Used by every
     * mutating operation (create sale, receive purchase, create expense,
     * ...): the branchId on the incoming request DTO is only ever used to
     * ask "is this branch allowed?", never taken as ground truth.
     */
    public void assertCanWriteToBranch(UUID branchId) {
        UserPrincipal user = currentUser();
        if (branchId == null) {
            throw new ForbiddenException("A branch is required for this operation");
        }
        if (user.isSuperAdmin()) {
            assertBranchExists(branchId);
            return;
        }
        UUID ownBranchId = requireOwnBranch(user);
        if (!ownBranchId.equals(branchId)) {
            throw ForbiddenException.branchAccessDenied(branchId);
        }
    }

    /**
     * For creating a transfer REQUEST: the actor only needs to be acting
     * for one side of the transfer (the store asking for stock, or the
     * warehouse offering it), not both - assertCanWriteToBranches would
     * wrongly reject every non-admin, since no branch user's single
     * assigned branch is ever both the source and destination. SUPER_ADMIN
     * passes trivially, as with every other branch check.
     */
    public void assertCanWriteToEitherBranch(UUID branchIdA, UUID branchIdB) {
        UserPrincipal user = currentUser();
        if (user.isSuperAdmin()) {
            assertBranchExists(branchIdA);
            assertBranchExists(branchIdB);
            return;
        }
        UUID ownBranchId = requireOwnBranch(user);
        if (!ownBranchId.equals(branchIdA) && !ownBranchId.equals(branchIdB)) {
            throw new ForbiddenException(
                    "This transfer does not involve a branch you have access to");
        }
    }

    /**
     * Whether the given branch pair is readable by the current user - used
     * by transfer search/get, where a transfer should be visible if EITHER
     * side is a branch the caller can see (not both).
     */
    public boolean canReadEitherBranch(UUID branchIdA, UUID branchIdB) {
        UserPrincipal user = currentUser();
        if (user.isSuperAdmin()) {
            return true;
        }
        UUID ownBranchId = user.getBranchId();
        return ownBranchId != null && (ownBranchId.equals(branchIdA) || ownBranchId.equals(branchIdB));
    }

    private UUID requireOwnBranch(UserPrincipal user) {
        if (user.getBranchId() == null) {
            throw new ForbiddenException("User has no assigned branch");
        }
        return user.getBranchId();
    }

    private void assertBranchExists(UUID branchId) {
        if (!branchRepository.existsById(branchId)) {
            throw com.company.erp.common.exception.ResourceNotFoundException.of("Branch", branchId);
        }
    }

    public enum BranchScope {
        ALL, STORES, WAREHOUSES, SPECIFIC
    }
}
