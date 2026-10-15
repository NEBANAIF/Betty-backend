-- Which products belong at which branches. A row here means "allowed" - there is no boolean to
-- flip, presence is the whole fact. A product with no row for a branch is invisible there:
-- left out of that branch's product list, POS, purchases, batches, and transfers to or from it.
--
-- Seeded here as every existing product x every existing branch, so nothing that already worked
-- breaks: everything starts fully checked, and an administrator unchecks what doesn't belong on
-- each branch's product checklist (Branches > Manage Products, or wherever the frontend puts it).
-- New products and new branches get seeded the same way from now on (see ProductService#create
-- and BranchService#createBranch).

CREATE TABLE product_branch_availability (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID        NOT NULL REFERENCES products (id),
    branch_id   UUID        NOT NULL REFERENCES branches (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_branch_availability UNIQUE (product_id, branch_id)
);

CREATE INDEX idx_product_branch_availability_branch ON product_branch_availability (branch_id);
CREATE INDEX idx_product_branch_availability_product ON product_branch_availability (product_id);

INSERT INTO product_branch_availability (product_id, branch_id)
SELECT p.id, b.id FROM products p CROSS JOIN branches b;
