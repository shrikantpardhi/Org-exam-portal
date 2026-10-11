-- ---------------------------------------------------------------------------
-- V8 - delivery_policy (Delivery-owned prerequisite for P4)
--
-- The frozen schema declares assessment_version.delivery_policy_id -> delivery_policy.
-- delivery_policy is owned by the Delivery domain, which is implemented in a later phase.
-- It is created here, with its exact frozen columns (specs/04-Database-Schema-V1.5.md §29.6),
-- so the frozen foreign key holds. P4 only reads it through the Delivery application contract;
-- the Delivery phase owns and manages it. Recorded in docs/known-gaps.md (GAP-040).
-- ---------------------------------------------------------------------------

CREATE TABLE delivery_policy (
    id            UUID         NOT NULL,
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    configuration JSONB,
    status        VARCHAR(20)  NOT NULL,
    created_by    UUID,
    created_on    TIMESTAMPTZ,
    updated_by    UUID,
    updated_on    TIMESTAMPTZ,
    CONSTRAINT pk_delivery_policy PRIMARY KEY (id),
    CONSTRAINT uk_delivery_policy_code UNIQUE (code),
    CONSTRAINT ck_delivery_policy_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);
