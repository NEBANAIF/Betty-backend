package com.company.erp.roles;

import com.company.erp.roles.dto.AccessRoleRequest;
import com.company.erp.roles.dto.AccessRoleResponse;
import com.company.erp.roles.dto.PermissionInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Roles & Permissions. Reading needs USER_MANAGE (the Users page also lists roles);
 * creating/editing/deleting additionally requires an actual Super Admin - see
 * AccessRoleService.
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class RolesController {

    private final AccessRoleService accessRoleService;

    @GetMapping
    public List<AccessRoleResponse> list() {
        return accessRoleService.list();
    }

    @GetMapping("/catalog")
    public List<PermissionInfo> catalog() {
        return accessRoleService.catalog();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccessRoleResponse create(@Valid @RequestBody AccessRoleRequest request) {
        return accessRoleService.create(request);
    }

    @PutMapping("/{id}")
    public AccessRoleResponse update(@PathVariable UUID id, @Valid @RequestBody AccessRoleRequest request) {
        return accessRoleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        accessRoleService.delete(id);
    }
}
