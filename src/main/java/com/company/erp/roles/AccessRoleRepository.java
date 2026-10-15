package com.company.erp.roles;

import com.company.erp.user.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccessRoleRepository extends JpaRepository<AccessRole, UUID> {

    boolean existsByNameIgnoreCase(String name);

    /** The built-in role for a structural kind (e.g. the built-in "Store Staff"). */
    Optional<AccessRole> findBySystemRoleTrueAndBaseRole(Role baseRole);
}
