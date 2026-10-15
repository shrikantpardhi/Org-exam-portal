-- ---------------------------------------------------------------------------
-- V18 - Commerce schema (P9-02 … P9-13)
--
-- Creates the seven frozen Commerce tables from specs/18-P9-Commerce-V1.5.md section 7 and
-- specs/04-Database-Schema-V1.5.md section 32:
--
--   access_package, access_package_collection, subscription_plan, purchase, payment_record,
--   access_grant, coupon
--
-- Design notes:
--   * Columns, types, keys, unique constraints and indexes are taken verbatim from the frozen
--     schema. No table or column is invented (§139): no payment_event, refund, grant_source,
--     purchase_status, or coupon_usage table.
--   * AccessGrant is the authorization source of truth; Purchase and PaymentRecord never
--     authorize access (specs/18-P9 §3.1, §3.2).
--   * Status columns use controlled VARCHAR + CHECK (the project enum strategy).
--   * purchase and payment_record carry `payment_status`, not a generic lifecycle `status`.
--   * A UNIQUE constraint on payment_record.payment_reference is applied because the selected
--     provider guarantees globally unique references and it is the callback idempotency anchor
--     (§21, §32.5). Postgres allows multiple NULLs, so a PENDING record may exist before initiation.
--   * Migration numbering: earlier phases used V2-V17, so Commerce is V18/V19.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- access_package
-- ---------------------------------------------------------------------------
CREATE TABLE access_package (
    id          UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    CONSTRAINT pk_access_package PRIMARY KEY (id),
    CONSTRAINT uk_access_package_code UNIQUE (code),
    CONSTRAINT ck_access_package_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

-- ---------------------------------------------------------------------------
-- access_package_collection
-- ---------------------------------------------------------------------------
CREATE TABLE access_package_collection (
    id                       UUID        NOT NULL,
    access_package_id        UUID        NOT NULL,
    assessment_collection_id UUID        NOT NULL,
    created_by               UUID,
    created_on               TIMESTAMPTZ,
    updated_by               UUID,
    updated_on               TIMESTAMPTZ,
    status                   VARCHAR(20) NOT NULL,
    CONSTRAINT pk_access_package_collection PRIMARY KEY (id),
    CONSTRAINT uk_access_package_collection_package_collection UNIQUE (access_package_id, assessment_collection_id),
    CONSTRAINT ck_access_package_collection_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_access_package_collection_package FOREIGN KEY (access_package_id) REFERENCES access_package (id),
    CONSTRAINT fk_access_package_collection_collection FOREIGN KEY (assessment_collection_id) REFERENCES assessment_collection (id)
);

CREATE INDEX idx_access_package_collection_access_package_id ON access_package_collection (access_package_id);
CREATE INDEX idx_access_package_collection_assessment_collection_id ON access_package_collection (assessment_collection_id);

-- ---------------------------------------------------------------------------
-- subscription_plan
-- ---------------------------------------------------------------------------
CREATE TABLE subscription_plan (
    id                UUID          NOT NULL,
    access_package_id UUID          NOT NULL,
    code              VARCHAR(50)   NOT NULL,
    name              VARCHAR(150)  NOT NULL,
    duration_days     INTEGER       NOT NULL,
    price             NUMERIC(10, 2) NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    created_by        UUID,
    created_on        TIMESTAMPTZ,
    updated_by        UUID,
    updated_on        TIMESTAMPTZ,
    CONSTRAINT pk_subscription_plan PRIMARY KEY (id),
    CONSTRAINT uk_subscription_plan_code UNIQUE (code),
    CONSTRAINT ck_subscription_plan_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_subscription_plan_duration CHECK (duration_days > 0),
    CONSTRAINT ck_subscription_plan_price CHECK (price >= 0),
    CONSTRAINT fk_subscription_plan_access_package FOREIGN KEY (access_package_id) REFERENCES access_package (id)
);

CREATE INDEX idx_subscription_plan_access_package_id ON subscription_plan (access_package_id);

-- ---------------------------------------------------------------------------
-- coupon
-- ---------------------------------------------------------------------------
CREATE TABLE coupon (
    id             UUID          NOT NULL,
    code           VARCHAR(50)   NOT NULL,
    name           VARCHAR(150)  NOT NULL,
    discount_value NUMERIC(10, 2) NOT NULL,
    valid_from     TIMESTAMPTZ,
    valid_to       TIMESTAMPTZ,
    status         VARCHAR(20)   NOT NULL,
    created_by     UUID,
    created_on     TIMESTAMPTZ,
    updated_by     UUID,
    updated_on     TIMESTAMPTZ,
    CONSTRAINT pk_coupon PRIMARY KEY (id),
    CONSTRAINT uk_coupon_code UNIQUE (code),
    CONSTRAINT ck_coupon_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_coupon_discount CHECK (discount_value >= 0)
);

-- ---------------------------------------------------------------------------
-- purchase
-- ---------------------------------------------------------------------------
CREATE TABLE purchase (
    id                   UUID           NOT NULL,
    user_id              UUID           NOT NULL,
    subscription_plan_id UUID           NOT NULL,
    coupon_id            UUID,
    purchase_date        TIMESTAMPTZ,
    amount               NUMERIC(10, 2) NOT NULL,
    payment_status       VARCHAR(20)    NOT NULL,
    created_by           UUID,
    created_on           TIMESTAMPTZ,
    updated_by           UUID,
    updated_on           TIMESTAMPTZ,
    CONSTRAINT pk_purchase PRIMARY KEY (id),
    CONSTRAINT ck_purchase_payment_status CHECK (payment_status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
    CONSTRAINT ck_purchase_amount CHECK (amount >= 0),
    CONSTRAINT fk_purchase_user FOREIGN KEY (user_id) REFERENCES "user" (id),
    CONSTRAINT fk_purchase_subscription_plan FOREIGN KEY (subscription_plan_id) REFERENCES subscription_plan (id),
    CONSTRAINT fk_purchase_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id)
);

CREATE INDEX idx_purchase_user_id ON purchase (user_id);
CREATE INDEX idx_purchase_subscription_plan_id ON purchase (subscription_plan_id);
CREATE INDEX idx_purchase_coupon_id ON purchase (coupon_id);
CREATE INDEX idx_purchase_purchase_date ON purchase (purchase_date);
CREATE INDEX idx_purchase_payment_status ON purchase (payment_status);

-- ---------------------------------------------------------------------------
-- payment_record
-- ---------------------------------------------------------------------------
CREATE TABLE payment_record (
    id                UUID           NOT NULL,
    purchase_id       UUID           NOT NULL,
    payment_reference VARCHAR(255),
    payment_method    VARCHAR(100),
    payment_status    VARCHAR(20)    NOT NULL,
    payment_date      TIMESTAMPTZ,
    created_by        UUID,
    created_on        TIMESTAMPTZ,
    updated_by        UUID,
    updated_on        TIMESTAMPTZ,
    CONSTRAINT pk_payment_record PRIMARY KEY (id),
    CONSTRAINT uk_payment_record_purchase_id UNIQUE (purchase_id),
    CONSTRAINT uk_payment_record_payment_reference UNIQUE (payment_reference),
    CONSTRAINT ck_payment_record_payment_status CHECK (payment_status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
    CONSTRAINT fk_payment_record_purchase FOREIGN KEY (purchase_id) REFERENCES purchase (id)
);

CREATE INDEX idx_payment_record_payment_reference ON payment_record (payment_reference);
CREATE INDEX idx_payment_record_payment_status ON payment_record (payment_status);

-- ---------------------------------------------------------------------------
-- access_grant
-- ---------------------------------------------------------------------------
CREATE TABLE access_grant (
    id                UUID        NOT NULL,
    user_id           UUID        NOT NULL,
    access_package_id UUID        NOT NULL,
    start_date        TIMESTAMPTZ NOT NULL,
    end_date          TIMESTAMPTZ,
    status            VARCHAR(20) NOT NULL,
    created_by        UUID,
    created_on        TIMESTAMPTZ,
    updated_by        UUID,
    updated_on        TIMESTAMPTZ,
    CONSTRAINT pk_access_grant PRIMARY KEY (id),
    CONSTRAINT ck_access_grant_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_access_grant_user FOREIGN KEY (user_id) REFERENCES "user" (id),
    CONSTRAINT fk_access_grant_access_package FOREIGN KEY (access_package_id) REFERENCES access_package (id)
);

CREATE INDEX idx_access_grant_user_id ON access_grant (user_id);
CREATE INDEX idx_access_grant_access_package_id ON access_grant (access_package_id);
CREATE INDEX idx_access_grant_start_date ON access_grant (start_date);
CREATE INDEX idx_access_grant_end_date ON access_grant (end_date);
CREATE INDEX idx_access_grant_status ON access_grant (status);
