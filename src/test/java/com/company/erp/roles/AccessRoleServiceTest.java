package com.company.erp.roles;

import com.company.erp.common.audit.BaseEntity;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.DuplicateResourceException;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.roles.dto.AccessRoleRequest;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.UserPrincipal;
import com.company.erp.user.Role;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessRoleServiceTest {

    @Mock private AccessRoleRepository accessRoleRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchAccessService branchAccessService;

    private AccessRoleService service;

    @BeforeEach
    void setUp() {
        service = new AccessRoleService(accessRoleRepository, userRepository, branchAccessService);
    }

    private void actingAs(Role role) {
        User caller = new User();
        caller.setName("Caller");
        caller.setEmail("caller@test.com");
        caller.setPasswordHash("x");
        caller.setRole(role);
        when(branchAccessService.currentUser()).thenReturn(new UserPrincipal(caller));
    }

    private AccessRoleRequest request(String name, String branchType, boolean manager, String... permissions) {
        return new AccessRoleRequest(name, null, branchType, manager, new HashSet<>(Set.of(permissions)));
    }

    private AccessRole storedRole(String name, Role base, boolean system, String... permissions) {
        AccessRole role = new AccessRole();
        role.setName(name);
        role.setBaseRole(base);
        role.setSystemRole(system);
        role.setPermissions(new HashSet<>(Set.of(permissions)));
        try {
            var field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(role, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return role;
    }

    @Test
    void aNewRoleGetsTheChosenKindAndExactlyTheChosenPermissions() {
        actingAs(Role.SUPER_ADMIN);
        when(accessRoleRepository.existsByNameIgnoreCase("Cashier")).thenReturn(false);
        when(accessRoleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(request("Cashier", "STORE", false, "POS_ACCESS", "SALES_CREATE"));

        assertThat(response.baseRole()).isEqualTo("STORE_STAFF");
        assertThat(response.systemRole()).isFalse();
        assertThat(response.permissions()).containsExactlyInAnyOrder("POS_ACCESS", "SALES_CREATE");
    }

    @Test
    void theTwoChoicesMapToTheFourStructuralKinds() {
        assertThat(AccessRoleService.baseRoleFor("STORE", false)).isEqualTo(Role.STORE_STAFF);
        assertThat(AccessRoleService.baseRoleFor("STORE", true)).isEqualTo(Role.STORE_MANAGER);
        assertThat(AccessRoleService.baseRoleFor("WAREHOUSE", false)).isEqualTo(Role.WAREHOUSE_STAFF);
        assertThat(AccessRoleService.baseRoleFor("warehouse", true)).isEqualTo(Role.WAREHOUSE_MANAGER);
        assertThatThrownBy(() -> AccessRoleService.baseRoleFor(null, false))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> AccessRoleService.baseRoleFor("HEADQUARTERS", false))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void aDuplicateRoleNameIsRejectedRegardlessOfCase() {
        actingAs(Role.SUPER_ADMIN);
        when(accessRoleRepository.existsByNameIgnoreCase("cashier")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("cashier", "STORE", false)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void anUnknownPermissionIsRejectedRatherThanSilentlyStored() {
        actingAs(Role.SUPER_ADMIN);
        when(accessRoleRepository.existsByNameIgnoreCase("Cashier")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request("Cashier", "STORE", false, "NOT_A_REAL_PERMISSION")))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(accessRoleRepository, never()).save(any());
    }

    @Test
    void onlyASuperAdminCanCreateRolesEvenWithUserManagePermission() {
        actingAs(Role.STORE_MANAGER);

        assertThatThrownBy(() -> service.create(request("Sneaky", "STORE", false, "USER_MANAGE")))
                .isInstanceOf(ForbiddenException.class);
        verify(accessRoleRepository, never()).save(any());
    }

    @Test
    void theSuperAdminRoleCannotBeEdited() {
        actingAs(Role.SUPER_ADMIN);
        AccessRole superAdmin = storedRole("Super Admin", Role.SUPER_ADMIN, true);
        when(accessRoleRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));

        assertThatThrownBy(() -> service.update(superAdmin.getId(), request("Super Admin", null, false, "POS_ACCESS")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void aBuiltInRoleKeepsItsNameButItsPermissionsCanChange() {
        actingAs(Role.SUPER_ADMIN);
        AccessRole storeStaff = storedRole("Store Staff", Role.STORE_STAFF, true, "POS_ACCESS", "VIEW_PROFIT");
        when(accessRoleRepository.findById(storeStaff.getId())).thenReturn(Optional.of(storeStaff));
        when(userRepository.countByAccessRoleId(storeStaff.getId())).thenReturn(3L);

        var response = service.update(storeStaff.getId(), request("Renamed!", null, false, "POS_ACCESS", "SALES_CREATE"));

        assertThat(response.name()).isEqualTo("Store Staff");
        assertThat(response.permissions()).containsExactlyInAnyOrder("POS_ACCESS", "SALES_CREATE");
        assertThat(response.userCount()).isEqualTo(3L);
    }

    @Test
    void aCustomRoleCanBeRenamedAndItsPermissionsReplaced() {
        actingAs(Role.SUPER_ADMIN);
        AccessRole cashier = storedRole("Cashier", Role.STORE_STAFF, false, "POS_ACCESS");
        when(accessRoleRepository.findById(cashier.getId())).thenReturn(Optional.of(cashier));
        when(accessRoleRepository.existsByNameIgnoreCase("Senior Cashier")).thenReturn(false);
        when(userRepository.countByAccessRoleId(cashier.getId())).thenReturn(0L);

        var response = service.update(cashier.getId(), request("Senior Cashier", null, false, "POS_ACCESS", "POS_DISCOUNT"));

        assertThat(response.name()).isEqualTo("Senior Cashier");
        assertThat(response.permissions()).containsExactlyInAnyOrder("POS_ACCESS", "POS_DISCOUNT");
    }

    @Test
    void aRoleThatStillHasUsersCannotBeDeleted() {
        actingAs(Role.SUPER_ADMIN);
        AccessRole cashier = storedRole("Cashier", Role.STORE_STAFF, false);
        when(accessRoleRepository.findById(cashier.getId())).thenReturn(Optional.of(cashier));
        when(userRepository.countByAccessRoleId(cashier.getId())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(cashier.getId())).isInstanceOf(BusinessRuleViolationException.class);
        verify(accessRoleRepository, never()).delete(any());
    }

    @Test
    void theBuiltInRolesCannotBeDeleted() {
        actingAs(Role.SUPER_ADMIN);
        AccessRole builtIn = storedRole("Store Staff", Role.STORE_STAFF, true);
        when(accessRoleRepository.findById(builtIn.getId())).thenReturn(Optional.of(builtIn));

        assertThatThrownBy(() -> service.delete(builtIn.getId())).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void anUnusedCustomRoleCanBeDeleted() {
        actingAs(Role.SUPER_ADMIN);
        AccessRole cashier = storedRole("Cashier", Role.STORE_STAFF, false);
        when(accessRoleRepository.findById(cashier.getId())).thenReturn(Optional.of(cashier));
        when(userRepository.countByAccessRoleId(cashier.getId())).thenReturn(0L);

        service.delete(cashier.getId());

        verify(accessRoleRepository).delete(cashier);
    }
}
