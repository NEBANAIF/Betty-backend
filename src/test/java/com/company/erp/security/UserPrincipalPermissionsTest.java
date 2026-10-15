package com.company.erp.security;

import com.company.erp.roles.AccessRole;
import com.company.erp.user.Permission;
import com.company.erp.user.Role;
import com.company.erp.user.User;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserPrincipalPermissionsTest {

    private User userWith(Role kind, AccessRole accessRole) {
        User user = new User();
        user.setName("Someone");
        user.setEmail("someone@test.com");
        user.setPasswordHash("x");
        user.setRole(kind);
        user.setAccessRole(accessRole);
        return user;
    }

    private AccessRole roleWith(Role base, String... permissions) {
        AccessRole role = new AccessRole();
        role.setName("Custom");
        role.setBaseRole(base);
        role.setPermissions(new HashSet<>(Set.of(permissions)));
        return role;
    }

    @Test
    void aUserGetsExactlyTheirStoredRolesPermissions() {
        var principal = new UserPrincipal(userWith(Role.STORE_STAFF, roleWith(Role.STORE_STAFF, "POS_ACCESS", "SALES_CREATE")));

        assertThat(principal.getPermissions()).containsExactlyInAnyOrder(Permission.POS_ACCESS, Permission.SALES_CREATE);
        assertThat(principal.hasPermission(Permission.VIEW_PROFIT)).isFalse();
        assertThat(principal.getAuthorities()).extracting(Object::toString)
                .contains("POS_ACCESS", "ROLE_STORE_STAFF")
                .doesNotContain("VIEW_PROFIT");
    }

    @Test
    void aPermissionNameThatNoLongerExistsIsIgnoredInsteadOfBreakingLogin() {
        var principal = new UserPrincipal(userWith(Role.STORE_STAFF, roleWith(Role.STORE_STAFF, "POS_ACCESS", "REMOVED_LONG_AGO")));

        assertThat(principal.getPermissions()).containsExactly(Permission.POS_ACCESS);
    }

    @Test
    void theSuperAdminAlwaysHasEveryPermissionWhateverIsStored() {
        var principal = new UserPrincipal(userWith(Role.SUPER_ADMIN, roleWith(Role.SUPER_ADMIN)));

        assertThat(principal.getPermissions()).containsAll(Set.of(Permission.values()));
        assertThat(principal.isSuperAdmin()).isTrue();
    }

    @Test
    void theRoleNameShownIsTheStoredRolesNotTheStructuralKind() {
        var principal = new UserPrincipal(userWith(Role.STORE_STAFF, roleWith(Role.STORE_STAFF, "POS_ACCESS")));

        assertThat(principal.getRoleName()).isEqualTo("Custom");
        assertThat(principal.getRole()).isEqualTo(Role.STORE_STAFF);
    }
}
