package com.company.erp.user;

import java.time.Instant;
import com.company.erp.common.query.SearchSpecs;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Backs the Administration > Users list (spec section 29). Never
     * branch-access-scoped like every other search in the app - this
     * endpoint is USER_MANAGE-gated (SUPER_ADMIN only in the fixed
     * permission set), so branchId here is a plain optional filter, not
     * an authorization boundary.
     */
    default Page<User> search(UUID branchId, Role role, String search, Pageable pageable) {
        return search(branchId, role, search, null, null, null, pageable);
    }

    /** Same again, plus an optional active/inactive filter and a range on when the account was created. */
    default Page<User> search(UUID branchId, Role role, String search, Boolean active,
                              Instant from, Instant to, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (branchId != null) {
                p.add(cb.equal(root.get("branch").get("id"), branchId));
            }
            if (role != null) {
                p.add(cb.equal(root.get("role"), role));
            }
            if (SearchSpecs.hasText(search)) {
                String term = search.trim();
                p.add(cb.or(
                        SearchSpecs.containsIgnoreCase(root.<String>get("name"), term, cb),
                        SearchSpecs.containsIgnoreCase(root.<String>get("email"), term, cb)));
            }
            if (active != null) {
                p.add(cb.equal(root.<Boolean>get("active"), active));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), to));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return findAll(spec, SearchSpecs.withDefaultSort(pageable, Sort.by(Sort.Direction.ASC, "name")));
    }

    long countByRoleAndActiveTrue(Role role);

    long countByAccessRoleId(UUID accessRoleId);
}
