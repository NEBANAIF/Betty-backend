package com.company.erp.security;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.branch.BranchType;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.user.Role;
import com.company.erp.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchAccessServiceTest {

    @Mock
    private BranchRepository branchRepository;

    private BranchAccessService branchAccessService;

    private final UUID boleStoreId = UUID.randomUUID();
    private final UUID mainWarehouseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        branchAccessService = new BranchAccessService(branchRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void storeStaffCanOnlyReadOwnBranch() {
        authenticateAs(Role.STORE_STAFF, boleStoreId);

        Optional<UUID> resolved = branchAccessService.resolveReadableBranchId(null);

        assertThat(resolved).contains(boleStoreId);
    }

    @Test
    void storeStaffRequestingAnotherBranchIsForbidden() {
        authenticateAs(Role.STORE_STAFF, boleStoreId);

        assertThatThrownBy(() -> branchAccessService.resolveReadableBranchId(mainWarehouseId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void storeStaffCannotWriteToAnotherBranch() {
        authenticateAs(Role.STORE_STAFF, boleStoreId);

        assertThatThrownBy(() -> branchAccessService.assertCanWriteToBranch(mainWarehouseId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void storeStaffCanWriteToOwnBranch() {
        authenticateAs(Role.STORE_STAFF, boleStoreId);

        // Should not throw.
        branchAccessService.assertCanWriteToBranch(boleStoreId);
    }

    @Test
    void superAdminWithNoFilterGetsAllBranches() {
        authenticateAs(Role.SUPER_ADMIN, null);

        Optional<UUID> resolved = branchAccessService.resolveReadableBranchId(null);

        assertThat(resolved).isEmpty(); // empty == "no filter, return everything" to the caller
    }

    @Test
    void superAdminCanReadAnyExistingBranch() {
        authenticateAs(Role.SUPER_ADMIN, null);
        when(branchRepository.existsById(mainWarehouseId)).thenReturn(true);

        Optional<UUID> resolved = branchAccessService.resolveReadableBranchId(mainWarehouseId);

        assertThat(resolved).contains(mainWarehouseId);
    }

    @Test
    void superAdminAllScopeReturnsEveryBranch() {
        authenticateAs(Role.SUPER_ADMIN, null);
        Branch main = branchOf(mainWarehouseId, BranchType.WAREHOUSE);
        Branch bole = branchOf(boleStoreId, BranchType.STORE);
        when(branchRepository.findAll()).thenReturn(List.of(main, bole));

        List<UUID> ids = branchAccessService.resolveReadableBranchIds(
                BranchAccessService.BranchScope.ALL, null);

        assertThat(ids).containsExactlyInAnyOrder(mainWarehouseId, boleStoreId);
    }

    @Test
    void nonAdminScopeRequestCollapsesToOwnBranchRegardlessOfScope() {
        authenticateAs(Role.WAREHOUSE_STAFF, mainWarehouseId);

        List<UUID> ids = branchAccessService.resolveReadableBranchIds(
                BranchAccessService.BranchScope.ALL, null);

        assertThat(ids).containsExactly(mainWarehouseId);
    }

    private void authenticateAs(Role role, UUID branchId) {
        User user = new User();
        user.setName("Test User");
        user.setEmail("test@erp.local");
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setActive(true);
        if (branchId != null) {
            Branch branch = new Branch();
            // BaseEntity#id has no public setter by design (generated); use
            // reflection-free approach via a real persisted-like branch is
            // unnecessary here - UserPrincipal only reads branch.getId(),
            // so we fake it through a subclass-free trick: set via Branch's
            // own id using JPA's generated value is not needed in a pure
            // unit test - assign with a test-only setter path instead.
            setBranchId(branch, branchId);
            user.setBranch(branch);
        }
        UserPrincipal principal = new UserPrincipal(user);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Branch branchOf(UUID id, BranchType type) {
        Branch branch = new Branch();
        setBranchId(branch, id);
        branch.setType(type);
        return branch;
    }

    /** BaseEntity#id is generated and has no public setter; reflection is used only in this test. */
    private void setBranchId(Branch branch, UUID id) {
        try {
            var field = com.company.erp.common.audit.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(branch, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
