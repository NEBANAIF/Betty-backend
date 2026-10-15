-- Phase 7: Loans / credit sales. A Loan always originates from exactly one
-- Sale (application-created only in PosCheckoutService, right after a sale
-- is persisted with remainingAmount > 0 - see LoanService.createFromSale).
--
-- status only ever transitions OPEN -> PARTIALLY_PAID -> PAID, driven
-- purely by payments applied against the loan (LoanService.recordPayment).
-- The domain also has an OVERDUE concept (spec section 6), but it is
-- deliberately NEVER written here: "overdue" is a function of
-- (status != PAID) and (due_date < today), which is always correct the
-- instant it's read and would otherwise need a nightly job to keep a
-- stored flag from going stale. See LoanService.effectiveStatus /
-- LoanResponse for where it's computed.
CREATE TABLE loans (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id       UUID           NOT NULL REFERENCES customers (id),
    sale_id           UUID           NOT NULL REFERENCES sales (id),
    branch_id         UUID           NOT NULL REFERENCES branches (id),
    original_amount   NUMERIC(14, 2) NOT NULL CHECK (original_amount > 0),
    paid_amount       NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    remaining_amount  NUMERIC(14, 2) NOT NULL CHECK (remaining_amount >= 0),
    status            VARCHAR(20)    NOT NULL CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'PAID')),
    due_date          DATE           NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version           BIGINT         NOT NULL DEFAULT 0,
    -- One loan per sale (ERD: SALE ||--o| LOAN, zero-or-one).
    CONSTRAINT uk_loan_sale UNIQUE (sale_id),
    CONSTRAINT chk_loan_paid_lte_original CHECK (paid_amount <= original_amount),
    CONSTRAINT chk_loan_remaining_matches CHECK (remaining_amount = original_amount - paid_amount)
);

CREATE INDEX idx_loans_customer ON loans (customer_id);
CREATE INDEX idx_loans_branch ON loans (branch_id);
CREATE INDEX idx_loans_status ON loans (status);
-- Backs the overdueOnly filter (remaining_amount > 0 AND due_date < today).
CREATE INDEX idx_loans_due_date ON loans (due_date) WHERE remaining_amount > 0;
