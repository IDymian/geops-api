-- Coupons, the codes consumers claim, and the audit log for sensitive actions.

-- A coupon is the provider's template for an offer: discount, stock and validity.
CREATE TABLE coupons (
    id             uuid          PRIMARY KEY,
    offer_id       uuid          NOT NULL REFERENCES offers (id) ON DELETE CASCADE,
    title          varchar(120)  NOT NULL,
    discount_type  varchar(10)   NOT NULL CHECK (discount_type IN ('PERCENT', 'FIXED')),
    discount_value numeric(10, 2) NOT NULL CHECK (discount_value > 0),
    stock          integer       CHECK (stock > 0),
    claimed_count  integer       NOT NULL DEFAULT 0,
    per_user_limit integer       NOT NULL DEFAULT 1 CHECK (per_user_limit > 0),
    valid_from     timestamptz   NOT NULL,
    valid_until    timestamptz   NOT NULL,
    created_at     timestamptz   NOT NULL,
    CONSTRAINT ck_coupons_period CHECK (valid_until > valid_from),
    CONSTRAINT ck_coupons_percent CHECK (discount_type <> 'PERCENT' OR discount_value <= 100),
    -- Last line of defense: the service already checks stock under a row lock.
    CONSTRAINT ck_coupons_stock CHECK (stock IS NULL OR claimed_count <= stock)
);

CREATE INDEX ix_coupons_offer ON coupons (offer_id);

-- The unique code a consumer receives. Redeemed once, by the business that issued it.
-- EXPIRED is computed when reading (valid_until has passed), so no job has to update rows.
CREATE TABLE user_coupons (
    id          uuid         PRIMARY KEY,
    coupon_id   uuid         NOT NULL REFERENCES coupons (id) ON DELETE CASCADE,
    user_id     uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code        varchar(16)  NOT NULL UNIQUE,
    status      varchar(10)  NOT NULL CHECK (status IN ('CLAIMED', 'REDEEMED', 'EXPIRED')),
    claimed_at  timestamptz  NOT NULL,
    redeemed_at timestamptz,
    redeemed_by uuid         REFERENCES users (id),
    CONSTRAINT ck_user_coupons_redeemed CHECK ((status = 'REDEEMED') = (redeemed_at IS NOT NULL))
);

CREATE INDEX ix_user_coupons_user ON user_coupons (user_id, claimed_at DESC);
CREATE INDEX ix_user_coupons_coupon_user ON user_coupons (coupon_id, user_id);

CREATE TABLE audit_log (
    id          bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id    uuid         REFERENCES users (id) ON DELETE SET NULL,
    action      varchar(60)  NOT NULL,
    entity_type varchar(60)  NOT NULL,
    entity_id   varchar(60)  NOT NULL,
    metadata    jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at  timestamptz  NOT NULL
);

CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id);
