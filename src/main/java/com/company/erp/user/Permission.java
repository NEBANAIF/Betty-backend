package com.company.erp.user;

/**
 * Every switch that can be turned on or off for a role. The list of switches
 * is defined here, in code (each one is enforced somewhere - a page, a button
 * or a piece of information); WHICH role holds WHICH switch is data, edited
 * on the Roles & Permissions page and stored in the database
 * (see roles.AccessRole).
 *
 * group / label / description are shown on that page, so a switch added here
 * appears in the role editor automatically.
 */
public enum Permission {

    DASHBOARD_VIEW("Dashboard", "Open the Dashboard",
            "Can open the Dashboard page. What appears on it is controlled by the other Dashboard switches."),
    DASHBOARD_REVENUE("Dashboard", "Sales count and revenue",
            "The number of sales and the revenue total."),
    DASHBOARD_EXPENSES("Dashboard", "Expenses",
            "The total expenses figure and the recent expenses list."),
    DASHBOARD_LOW_STOCK("Dashboard", "Low-stock and out-of-stock alerts",
            "The low/out-of-stock counts and the alerts list."),
    DASHBOARD_RECENT_SALES("Dashboard", "Recent sales",
            "The list of recent sales."),
    DASHBOARD_RECENT_TRANSFERS("Dashboard", "Recent transfers",
            "The list of recent stock transfers."),
    DASHBOARD_BRANCH_PERFORMANCE("Dashboard", "Branch performance",
            "The table comparing branches."),
    VIEW_COSTS("Sensitive information (wherever it appears)", "Cost prices",
            "What stock cost to buy: batch cost prices and product cost columns."),
    VIEW_PROFIT("Sensitive information (wherever it appears)", "Profit figures",
            "Cost of goods sold, gross profit, net profit and profit margin."),
    VIEW_INVENTORY_VALUE("Sensitive information (wherever it appears)", "Inventory value",
            "The total money value of the stock on hand."),
    POS_ACCESS("POS", "Open the POS",
            "Can open the point-of-sale screen."),
    POS_CHOOSE_BATCH("POS", "Choose the batch to sell from",
            "Can pick a specific batch on a sale line instead of the oldest-first default."),
    POS_DISCOUNT("POS", "Apply a discount",
            "Can give a discount on a sale."),
    SALES_VIEW("Sales", "View sales history",
            "Open the Sales History page."),
    CUSTOMER_VIEW("Sales", "View customers",
            "Open the Customers page. The POS can still look customers up for people who have POS access."),
    LOAN_VIEW("Sales", "View loans",
            "Open the Loans page: sales sold on credit and what is still owed."),
    RETURN_VIEW("Sales", "View customer returns",
            "See the list of customer returns on the Returns page."),
    SALES_CREATE("Sales", "Make sales",
            "Complete sales."),
    SALES_RETURN("Sales", "Process customer returns",
            "Take back goods from a customer."),
    SALES_VOID("Sales", "Void a sale",
            "Cancel a wrong or duplicate sale entirely and reverse its stock/financial impact. Blocked once any loan payment has been recorded against it."),
    INVENTORY_VIEW("Inventory", "View inventory",
            "Open the Inventory page: products and stock levels."),
    BATCH_VIEW("Inventory", "View batches",
            "Open the Batches page."),
    STOCK_HISTORY_VIEW("Inventory", "View stock history",
            "Open the Stock History page."),
    INVENTORY_EDIT("Inventory", "Edit inventory",
            "Change inventory settings."),
    PRODUCT_CREATE("Inventory", "Create products",
            "Add new products."),
    PRODUCT_EDIT("Inventory", "Edit products",
            "Change product details."),
    PRODUCT_DELETE("Inventory", "Delete products",
            "Remove (hide) products."),
    STOCK_ADJUST("Inventory", "Adjust stock",
            "Manually increase or decrease stock."),
    BATCH_CREATE("Inventory", "Add batches",
            "Enter stock as a new batch without a purchase order. It is held until someone approves it."),
    STOCK_APPROVE("Inventory", "Approve stock changes",
            "Approve or reject stock that was added or removed by hand. Stock only moves once it is approved."),
    PURCHASE_CREATE("Purchasing", "Create purchase orders",
            "Open Purchases and raise purchase orders."),
    PURCHASE_RECEIVE("Purchasing", "Receive purchases",
            "Receive stock against a purchase order."),
    SUPPLIER_MANAGE("Purchasing", "Manage suppliers",
            "Add and edit suppliers."),
    TRANSFER_CREATE("Transfers", "Create transfers",
            "Open Transfers and start a stock transfer."),
    TRANSFER_APPROVE("Transfers", "Approve transfers",
            "Approve a transfer out of a branch."),
    TRANSFER_RECEIVE("Transfers", "Receive transfers",
            "Receive a transfer into a branch."),
    FINANCE_VIEW("Finance", "View finance",
            "Open the Finance page: profit and loss and the list of transactions."),
    EXPENSE_VIEW("Finance", "View expenses list",
            "See the list of expenses on the Expenses page. Recording an expense is a separate switch."),
    EXPENSE_CREATE("Finance", "Record expenses",
            "Open Expenses and log an expense."),
    PAYMENT_VIEW("Payments", "Open Payments",
            "Open the Payments page and see the table of payments received."),
    BANK_VIEW("Payments", "See bank accounts",
            "See the registered banks and their account numbers (the Banks tab, and the bank account on each payment)."),
    BANK_MANAGE("Payments", "Register and edit banks",
            "Register new banks and edit or deactivate existing ones."),
    REPORT_VIEW("Reports", "View reports",
            "Open the Reports page."),
    ACTIVITY_VIEW("Activity", "See notifications",
            "Open the Notifications page and the bell: sales and stock changes for the branches this role can see."),
    ACTIVITY_REVIEW("Activity", "Review activity",
            "Put a sale or stock change on hold while it is checked, and approve it once it is correct."),
    USER_MANAGE("Administration", "Manage users and roles",
            "Open Users and Roles & Permissions."),
    BRANCH_MANAGE("Administration", "Manage branches",
            "Open Branches and add or edit branches."),
    SETTINGS_MANAGE("Administration", "Open Settings",
            "Open the Settings page.");

    private final String group;
    private final String label;
    private final String description;

    Permission(String group, String label, String description) {
        this.group = group;
        this.label = label;
        this.description = description;
    }

    public String getGroup() {
        return group;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
