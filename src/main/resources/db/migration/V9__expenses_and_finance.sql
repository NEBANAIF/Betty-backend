-- Phase 10: Expenses (spec section 23) and the financial ledger (section 24).
--
-- financial_transactions.amount is NOT constrained to >= 0: a customer
-- return posts negative SALE_REVENUE (and negative COGS, for the
-- restocked portion) as its own signed entry rather than mutating or
-- deleting the original sale's entry - see FinancialTransaction's javadoc.
CREATE TABLE expenses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id       UUID           NOT NULL REFERENCES branches (id),
    category        VARCHAR(20)    NOT NULL CHECK (category IN
                     ('RENT', 'ELECTRICITY', 'SALARIES', 'TRANSPORT', 'INTERNET', 'MAINTENANCE', 'OTHER')),
    amount          NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    description     VARCHAR(500)   NOT NULL,
    expense_date    DATE           NOT NULL,
    payment_method  VARCHAR(20)    NOT NULL CHECK (payment_method IN ('CASH', 'BANK', 'CREDIT')),
    reference       VARCHAR(255),
    created_by      UUID           NOT NULL REFERENCES app_users (id),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version         BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_expenses_branch_date ON expenses (branch_id, expense_date);
CREATE INDEX idx_expenses_category ON expenses (category);

CREATE TABLE financial_transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id       UUID           NOT NULL REFERENCES branches (id),
    type            VARCHAR(20)    NOT NULL CHECK (type IN
                     ('SALE_REVENUE', 'COGS', 'EXPENSE', 'PAYMENT_IN', 'PAYMENT_OUT')),
    amount          NUMERIC(14, 2) NOT NULL,
    occurred_on     DATE           NOT NULL,
    reference_type  VARCHAR(30)    NOT NULL CHECK (reference_type IN
                     ('SALE', 'EXPENSE', 'LOAN_PAYMENT', 'CUSTOMER_RETURN')),
    reference_id    UUID           NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version         BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_financial_transactions_branch_date ON financial_transactions (branch_id, occurred_on);
CREATE INDEX idx_financial_transactions_type ON financial_transactions (type);
CREATE INDEX idx_financial_transactions_reference ON financial_transactions (reference_type, reference_id);
