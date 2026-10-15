package com.company.erp.user;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.company.erp.user.Permission.*;

/**
 * The DEFAULT permission set of each of the five built-in roles.
 * <p>
 * Who can do what is no longer decided here: it is stored in the database
 * (roles.AccessRole) and edited on the Roles & Permissions page. This class is
 * only the starting point that the V13 migration seeded those rows from, plus a
 * safety net for a user object that carries no stored role. Keep it in step with
 * the seed in V13__access_roles.sql if the built-in defaults are ever changed
 * for new installs.
 */
public final class RolePermissionRegistry {

    private static final Map<Role, Set<Permission>> ROLE_PERMISSIONS = Map.of(
            Role.SUPER_ADMIN, EnumSet.allOf(Permission.class),
            Role.STORE_MANAGER, set(
                BATCH_VIEW, STOCK_HISTORY_VIEW, CUSTOMER_VIEW, LOAN_VIEW, RETURN_VIEW, EXPENSE_VIEW,
                DASHBOARD_VIEW, INVENTORY_VIEW, INVENTORY_EDIT, PRODUCT_CREATE, PRODUCT_EDIT,
                POS_ACCESS, SALES_VIEW, SALES_CREATE, SALES_RETURN, PURCHASE_CREATE, PURCHASE_RECEIVE,
                SUPPLIER_MANAGE, TRANSFER_CREATE, TRANSFER_APPROVE, TRANSFER_RECEIVE, STOCK_ADJUST, BATCH_CREATE, STOCK_APPROVE, EXPENSE_CREATE,
                FINANCE_VIEW, REPORT_VIEW, DASHBOARD_REVENUE, DASHBOARD_EXPENSES, DASHBOARD_LOW_STOCK,
                DASHBOARD_RECENT_SALES, DASHBOARD_RECENT_TRANSFERS, DASHBOARD_BRANCH_PERFORMANCE, VIEW_COSTS,
                VIEW_PROFIT, POS_CHOOSE_BATCH, POS_DISCOUNT, PAYMENT_VIEW, BANK_VIEW,
                ACTIVITY_VIEW, ACTIVITY_REVIEW),
            Role.STORE_STAFF, set(
                BATCH_VIEW, STOCK_HISTORY_VIEW, CUSTOMER_VIEW, LOAN_VIEW, RETURN_VIEW,
                DASHBOARD_VIEW, INVENTORY_VIEW, POS_ACCESS, SALES_VIEW, SALES_CREATE, TRANSFER_CREATE,
                TRANSFER_RECEIVE, EXPENSE_CREATE, DASHBOARD_LOW_STOCK, DASHBOARD_RECENT_SALES,
                DASHBOARD_RECENT_TRANSFERS, POS_CHOOSE_BATCH, POS_DISCOUNT, PAYMENT_VIEW),
            Role.WAREHOUSE_MANAGER, set(
                BATCH_VIEW, STOCK_HISTORY_VIEW, LOAN_VIEW, EXPENSE_VIEW,
                DASHBOARD_VIEW, INVENTORY_VIEW, INVENTORY_EDIT, PRODUCT_CREATE, PRODUCT_EDIT,
                PURCHASE_CREATE, PURCHASE_RECEIVE, SUPPLIER_MANAGE, TRANSFER_CREATE, TRANSFER_APPROVE,
                TRANSFER_RECEIVE, STOCK_ADJUST, BATCH_CREATE, STOCK_APPROVE, EXPENSE_CREATE, FINANCE_VIEW, REPORT_VIEW, DASHBOARD_EXPENSES,
                DASHBOARD_LOW_STOCK, DASHBOARD_RECENT_TRANSFERS, DASHBOARD_BRANCH_PERFORMANCE, VIEW_COSTS,
                VIEW_INVENTORY_VALUE, PAYMENT_VIEW, BANK_VIEW,
                ACTIVITY_VIEW, ACTIVITY_REVIEW),
            Role.WAREHOUSE_STAFF, set(
                BATCH_VIEW, STOCK_HISTORY_VIEW,
                DASHBOARD_VIEW, INVENTORY_VIEW, PURCHASE_RECEIVE, TRANSFER_CREATE, TRANSFER_RECEIVE, EXPENSE_CREATE,
                DASHBOARD_LOW_STOCK, DASHBOARD_RECENT_TRANSFERS));

    private RolePermissionRegistry() {
    }

    private static Set<Permission> set(Permission... permissions) {
        return Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(permissions)));
    }

    public static Set<Permission> permissionsFor(Role role) {
        return ROLE_PERMISSIONS.getOrDefault(role, Set.of());
    }

    public static boolean hasPermission(Role role, Permission permission) {
        return permissionsFor(role).contains(permission);
    }
}
