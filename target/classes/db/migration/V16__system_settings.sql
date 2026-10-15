-- App-wide toggles that aren't tied to a branch or a role. Single-row table -
-- see SystemSettings' javadoc for why this isn't a key/value store.
--
-- lock_cost_price: when true, the New Product form's initial cost-price
-- field is shown disabled, for shops where the person entering products
-- doesn't know (or shouldn't set) cost. Starts FALSE so existing behavior
-- (cost editable) is unchanged until a SETTINGS_MANAGE user turns it on.

CREATE TABLE system_settings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lock_cost_price BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0
);

-- The single row this table will ever hold - the service also creates one
-- lazily on first GET/PUT if it's ever missing, but seeding it here means
-- the row (and its default) exists from the start rather than only after
-- someone first opens Settings.
INSERT INTO system_settings (lock_cost_price) VALUES (FALSE);
