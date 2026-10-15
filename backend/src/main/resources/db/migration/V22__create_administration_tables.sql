-- ---------------------------------------------------------------------------
-- V22 - Administration schema (P11-02, P11-03, P11-04, P11-05)
--
-- Creates exactly the four frozen Administration tables from
-- specs/20-P11-Administration-V1.5.md section 7 and specs/04-Database-Schema-V1.5.md section 34:
--
--   organization_setting, business_rule, lookup, audit_log
--
-- Design notes:
--   * Administration owns these four tables and no others. No support_ticket, incident,
--     admin_action, import_history, bulk_operation, search_index, health_history or
--     configuration_version table is created (specs/20-P11 §7).
--   * JSONB is used only for configurable structures (organization_setting.configuration,
--     business_rule.configuration). File-system paths, scripts, secrets and domain rows are not
--     stored there (specs/20-P11 §8.3, §9.3).
--   * audit_log is insert-only. It carries no request_id/old_values/new_values/ip/payload column
--     (specs/20-P11 §11.3) and has no update/delete endpoint; the entity is mapped immutable.
--   * lookup has display_order INTEGER with gaps allowed and is unique on (category, code).
--   * Migration numbering: earlier phases used V2-V21, so Administration is V22/V23.
-- ---------------------------------------------------------------------------

CREATE TABLE organization_setting (
    id              UUID         NOT NULL,
    organization_id UUID         NOT NULL,
    configuration   JSONB        NOT NULL,
    created_by      UUID,
    created_on      TIMESTAMPTZ,
    updated_by      UUID,
    updated_on      TIMESTAMPTZ,
    status          VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_organization_setting PRIMARY KEY (id),
    CONSTRAINT uk_organization_setting_organization UNIQUE (organization_id),
    CONSTRAINT ck_organization_setting_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_organization_setting_organization FOREIGN KEY (organization_id)
        REFERENCES organization (id)
);

CREATE TABLE business_rule (
    id            UUID        NOT NULL,
    code          VARCHAR(100) NOT NULL,
    configuration JSONB       NOT NULL,
    status        VARCHAR(20) NOT NULL,
    created_by    UUID,
    created_on    TIMESTAMPTZ,
    updated_by    UUID,
    updated_on    TIMESTAMPTZ,
    CONSTRAINT pk_business_rule PRIMARY KEY (id),
    CONSTRAINT uk_business_rule_code UNIQUE (code),
    CONSTRAINT ck_business_rule_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

CREATE TABLE lookup (
    id            UUID         NOT NULL,
    category      VARCHAR(100) NOT NULL,
    code          VARCHAR(100) NOT NULL,
    name          VARCHAR(150) NOT NULL,
    display_order INTEGER,
    status        VARCHAR(20)  NOT NULL,
    created_by    UUID,
    created_on    TIMESTAMPTZ,
    updated_by    UUID,
    updated_on    TIMESTAMPTZ,
    CONSTRAINT pk_lookup PRIMARY KEY (id),
    CONSTRAINT uk_lookup_category_code UNIQUE (category, code),
    CONSTRAINT ck_lookup_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_lookup_category ON lookup (category);
CREATE INDEX idx_lookup_category_display_order ON lookup (category, display_order);

CREATE TABLE audit_log (
    id          UUID         NOT NULL,
    user_id     UUID,
    entity_name VARCHAR(150),
    entity_id   UUID,
    action      VARCHAR(100),
    remarks     TEXT,
    created_on  TIMESTAMPTZ,
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_user FOREIGN KEY (user_id) REFERENCES "user" (id)
);

CREATE INDEX idx_audit_log_user_id ON audit_log (user_id);
CREATE INDEX idx_audit_log_entity_name ON audit_log (entity_name);
CREATE INDEX idx_audit_log_entity_id ON audit_log (entity_id);
CREATE INDEX idx_audit_log_created_on ON audit_log (created_on);
