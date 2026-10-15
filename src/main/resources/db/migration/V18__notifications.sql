-- Activity feed (Notifications page and bell).
--
-- notifications: one row per sale / stock change, shared by everyone who can see that branch.
--   The review columns (PENDING -> ON_HOLD -> APPROVED) are shared too: an activity is on hold or
--   approved for everybody. Nothing here blocks or reverses the activity itself.
-- notification_reads: "this user has read this notification" - per user, so one person clearing
--   their list does not clear anyone else's.

CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type            VARCHAR(30)  NOT NULL CHECK (type IN ('SALE_COMPLETED', 'SALE_VOIDED', 'STOCK_CHANGE')),
    title           VARCHAR(150) NOT NULL,
    message         VARCHAR(500) NOT NULL,
    branch_id       UUID         NOT NULL REFERENCES branches (id),
    actor_id        UUID REFERENCES app_users (id),
    reference_type  VARCHAR(30),
    reference_id    UUID,
    review_status   VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
                      CHECK (review_status IN ('PENDING', 'ON_HOLD', 'APPROVED')),
    reviewed_by     UUID REFERENCES app_users (id),
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_notifications_branch_created ON notifications (branch_id, created_at DESC);
CREATE INDEX idx_notifications_review_status ON notifications (review_status);

CREATE TABLE notification_reads (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id  UUID        NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    user_id          UUID        NOT NULL REFERENCES app_users (id),
    read_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_notification_read UNIQUE (notification_id, user_id)
);

CREATE INDEX idx_notification_reads_user ON notification_reads (user_id);

-- New permissions ACTIVITY_VIEW (see the feed) and ACTIVITY_REVIEW (hold / approve). Roles that can
-- already adjust stock - the Super Admin and the managers - get both; hand them to other roles on
-- the Roles & Permissions page.
INSERT INTO access_role_permissions (access_role_id, permission)
SELECT DISTINCT access_role_id, 'ACTIVITY_VIEW'
FROM access_role_permissions
WHERE permission = 'STOCK_ADJUST'
ON CONFLICT DO NOTHING;

INSERT INTO access_role_permissions (access_role_id, permission)
SELECT DISTINCT access_role_id, 'ACTIVITY_REVIEW'
FROM access_role_permissions
WHERE permission = 'STOCK_ADJUST'
ON CONFLICT DO NOTHING;
