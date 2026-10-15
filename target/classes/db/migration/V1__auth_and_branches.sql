-- Phase 2: authentication + the minimal branch shape users depend on.
-- Full branch business fields/behavior land in Phase 3's migration; this
-- table is intentionally already complete since Branch is simple and User
-- needs a real FK target.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE branches (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(255) NOT NULL,
    code           VARCHAR(50)  NOT NULL,
    type           VARCHAR(20)  NOT NULL CHECK (type IN ('STORE', 'WAREHOUSE')),
    address        VARCHAR(500),
    phone          VARCHAR(50),
    email          VARCHAR(255),
    manager_id     UUID,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_branch_name UNIQUE (name),
    CONSTRAINT uk_branch_code UNIQUE (code)
);

CREATE TABLE app_users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(255) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    phone          VARCHAR(50),
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(30)  NOT NULL CHECK (role IN
                    ('SUPER_ADMIN', 'STORE_MANAGER', 'STORE_STAFF', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')),
    branch_id      UUID REFERENCES branches (id),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_email UNIQUE (email),
    -- Enforces the invariant at the DB level too: non-admins must have a
    -- branch, SUPER_ADMIN must not.
    CONSTRAINT chk_super_admin_no_branch CHECK (
        (role = 'SUPER_ADMIN' AND branch_id IS NULL)
        OR (role != 'SUPER_ADMIN' AND branch_id IS NOT NULL)
    )
);

ALTER TABLE branches
    ADD CONSTRAINT fk_branch_manager FOREIGN KEY (manager_id) REFERENCES app_users (id);

CREATE INDEX idx_app_users_branch_id ON app_users (branch_id);
CREATE INDEX idx_app_users_role ON app_users (role);
