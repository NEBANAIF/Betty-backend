package com.company.erp.user;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.branch.BranchType;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.DuplicateResourceException;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.roles.AccessRole;
import com.company.erp.roles.AccessRoleRepository;
import com.company.erp.security.UserPrincipal;
import com.company.erp.user.dto.CreateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AccessRoleRepository accessRoleRepository;

    private UserService service;
    private Branch boleStore;
    private Branch mainWarehouse;
    private UUID boleStoreId;
    private UUID mainWarehouseId;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, branchRepository, passwordEncoder, accessRoleRepository);

        boleStoreId = UUID.randomUUID();
        boleStore = new Branch();
        boleStore.setName("Bole Store");
        boleStore.setType(BranchType.STORE);
        setId(boleStore, boleStoreId);

        mainWarehouseId = UUID.randomUUID();
        mainWarehouse = new Branch();
        mainWarehouse.setName("Main Warehouse");
        mainWarehouse.setType(BranchType.WAREHOUSE);
        setId(mainWarehouse, mainWarehouseId);

        lenient().when(passwordEncoder.encode(any())).thenReturn("hashed");
        // The five built-in roles exist (the V13 migration creates them).
        lenient().when(accessRoleRepository.findBySystemRoleTrueAndBaseRole(any()))
                .thenAnswer(inv -> Optional.of(role("Built-in " + inv.getArgument(0), inv.getArgument(0), true)));
        lenient().when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void superAdminMustNotHaveABranchEvenIfOneIsRequested() {
        var request = new CreateUserRequest("Root", "root@test.com", null, "password123", Role.SUPER_ADMIN, boleStoreId, null);

        assertThatThrownBy(() -> service.createUser(request)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void superAdminWithNoBranchIsCreatedFine() {
        when(userRepository.existsByEmailIgnoreCase("root@test.com")).thenReturn(false);

        var response = service.createUser(
                new CreateUserRequest("Root", "root@test.com", null, "password123", Role.SUPER_ADMIN, null, null));

        assertThat(response.branchId()).isNull();
        assertThat(response.role()).isEqualTo("SUPER_ADMIN");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"STORE_MANAGER", "STORE_STAFF", "WAREHOUSE_MANAGER", "WAREHOUSE_STAFF"})
    void everyNonAdminRoleRequiresABranch(Role role) {
        var request = new CreateUserRequest("Someone", "someone@test.com", null, "password123", role, null, null);

        assertThatThrownBy(() -> service.createUser(request)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void storeRolesCanOnlyBeAssignedToAStoreBranch() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(branchRepository.findById(mainWarehouseId)).thenReturn(Optional.of(mainWarehouse));

        var request = new CreateUserRequest("Confused Manager", "confused@test.com", null, "password123",
                Role.STORE_MANAGER, mainWarehouseId, null);

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("STORE");
    }

    @Test
    void warehouseRolesCanOnlyBeAssignedToAWarehouseBranch() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(branchRepository.findById(boleStoreId)).thenReturn(Optional.of(boleStore));

        var request = new CreateUserRequest("Confused Staff", "confused2@test.com", null, "password123",
                Role.WAREHOUSE_STAFF, boleStoreId, null);

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("WAREHOUSE");
    }

    @Test
    void aStoreManagerCorrectlyAssignedToAStoreIsCreatedFine() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(branchRepository.findById(boleStoreId)).thenReturn(Optional.of(boleStore));

        var response = service.createUser(new CreateUserRequest(
                "Bole Manager", "bole.manager@test.com", null, "password123", Role.STORE_MANAGER, boleStoreId, null));

        assertThat(response.branchId()).isEqualTo(boleStoreId);
    }

    @Test
    void duplicateEmailIsRejectedRegardlessOfCase() {
        when(userRepository.existsByEmailIgnoreCase("Bole.Manager@Test.com")).thenReturn(true);

        var request = new CreateUserRequest("Someone Else", "Bole.Manager@Test.com", null, "password123",
                Role.STORE_MANAGER, boleStoreId, null);

        assertThatThrownBy(() -> service.createUser(request)).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void passwordIsNeverStoredInPlainText() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(branchRepository.findById(boleStoreId)).thenReturn(Optional.of(boleStore));
        when(passwordEncoder.encode("SuperSecret123")).thenReturn("$2a$10$hashedvalue");

        service.createUser(new CreateUserRequest(
                "Bole Manager", "bole.manager@test.com", null, "SuperSecret123", Role.STORE_MANAGER, boleStoreId, null));

        org.mockito.Mockito.verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(
                user -> user.getPasswordHash().equals("$2a$10$hashedvalue")
                        && !user.getPasswordHash().equals("SuperSecret123")));
    }

    @Test
    void aCustomRoleFollowsTheBranchRulesOfItsStructuralKind() {
        UUID cashierRoleId = UUID.randomUUID();
        AccessRole cashier = role("Cashier", Role.STORE_STAFF, false);
        when(accessRoleRepository.findById(cashierRoleId)).thenReturn(Optional.of(cashier));
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(branchRepository.findById(mainWarehouseId)).thenReturn(Optional.of(mainWarehouse));
        when(branchRepository.findById(boleStoreId)).thenReturn(Optional.of(boleStore));

        // "Cashier" is a store role, so a warehouse is refused...
        var wrongBranch = new CreateUserRequest("Abel", "abel@test.com", null, "password123", null, mainWarehouseId, cashierRoleId);
        assertThatThrownBy(() -> service.createUser(wrongBranch))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("STORE");

        // ...and a store works; the user shows the custom role's name and its structural kind.
        var response = service.createUser(
                new CreateUserRequest("Abel", "abel@test.com", null, "password123", null, boleStoreId, cashierRoleId));
        assertThat(response.roleName()).isEqualTo("Cashier");
        assertThat(response.role()).isEqualTo("STORE_STAFF");
        assertThat(response.branchId()).isEqualTo(boleStoreId);
    }

    @Test
    void aRoleIsRequired() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);

        var request = new CreateUserRequest("No Role", "norole@test.com", null, "password123", null, boleStoreId, null);

        assertThatThrownBy(() -> service.createUser(request)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void someoneWhoIsNotASuperAdminCannotCreateASuperAdmin() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        User caller = new User();
        caller.setName("Manager");
        caller.setEmail("m@test.com");
        caller.setPasswordHash("x");
        caller.setRole(Role.STORE_MANAGER);
        setId(caller, UUID.randomUUID());
        UserPrincipal principal = new UserPrincipal(caller);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            var request = new CreateUserRequest("Root2", "root2@test.com", null, "password123", Role.SUPER_ADMIN, null, null);

            assertThatThrownBy(() -> service.createUser(request)).isInstanceOf(ForbiddenException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private AccessRole role(String name, Role baseRole, boolean systemRole) {
        AccessRole role = new AccessRole();
        role.setName(name);
        role.setBaseRole(baseRole);
        role.setSystemRole(systemRole);
        setId(role, UUID.randomUUID());
        return role;
    }

    @Test
    void setActiveTogglesAnExistingUser() {
        User user = new User();
        user.setActive(true);
        UUID userId = UUID.randomUUID();
        setId(user, userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var response = service.setActive(userId, false);

        assertThat(response.active()).isFalse();
        assertThat(user.isActive()).isFalse();
    }

    private void setId(BaseEntity entity, UUID id) {
        try {
            var field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
