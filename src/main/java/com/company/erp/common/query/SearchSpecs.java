package com.company.erp.common.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/**
 * Small helpers shared by every repository "search" method.
 * <p>
 * WHY THIS EXISTS: the original search queries were written as
 * {@code (:x IS NULL OR e.x = :x)} JPQL with optional filters bound as
 * {@code null}. On PostgreSQL with Hibernate 6 that pattern fails for
 * text, enum and date/time parameters (PostgreSQL cannot infer the type of
 * an untyped NULL bind), which surfaced as HTTP 500s on every list screen.
 * <p>
 * The search methods now build a JPA Specification instead, adding a
 * predicate ONLY when a filter is actually supplied - so no null is ever
 * bound to a query and the type-inference problem cannot occur.
 */
public final class SearchSpecs {

    private SearchSpecs() {
    }

    /**
     * {@code path IN (ids)}. An empty collection yields an always-false
     * predicate instead of an invalid empty IN list, so a user with no
     * accessible branches simply sees no rows.
     */
    public static Predicate idIn(Path<?> path, Collection<UUID> ids, CriteriaBuilder cb) {
        if (ids == null || ids.isEmpty()) {
            return cb.disjunction();
        }
        return path.in(ids);
    }

    /** Case-insensitive "contains" match. Any % _ \ in the term is matched literally. */
    public static Predicate containsIgnoreCase(Expression<String> expression, String term, CriteriaBuilder cb) {
        return cb.like(cb.lower(expression), likePattern(term.toLowerCase(Locale.ROOT)), '\\');
    }

    /** Case-sensitive "contains" match (used for phone numbers). */
    public static Predicate contains(Expression<String> expression, String term, CriteriaBuilder cb) {
        return cb.like(expression, likePattern(term), '\\');
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Keeps the caller's sort when one was supplied (e.g. the frontend's
     * {@code sort=createdAt,desc}); otherwise applies the default order the
     * old JPQL "ORDER BY" used to provide.
     */
    public static Pageable withDefaultSort(Pageable pageable, Sort defaultSort) {
        if (pageable == null || pageable.isUnpaged() || pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }

    private static String likePattern(String term) {
        String escaped = term
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
