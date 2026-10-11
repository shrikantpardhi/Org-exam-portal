-- ---------------------------------------------------------------------------
-- V9 - Assessment schema (P4-02)
--
-- Creates the nine frozen Assessment tables from specs/13-P4-Assessment-V1.5.md section 5
-- and specs/04-Database-Schema-V1.5.md section 28.
--
--   blueprint, blueprint_version, assessment, assessment_version, assessment_question,
--   assessment_selection_rule, assessment_schedule, assessment_collection, collection_assessment
--
-- Design notes:
--   * Columns, types, keys, unique constraints, indexes, the engine_type CHECK, and the
--     target_count CHECK are taken verbatim from the frozen schema.
--   * assessment_question.question_version_id references an EXACT question_version (never a
--     question_id or "latest"). UNIQUE(assessment_version_id, question_version_id) is enforced
--     so a QuestionVersion appears once per AssessmentVersion (§5.5, §123).
--   * assessment_schedule.mode uses a CHECK on the frozen PRACTICE/EXAM value set (§10 strategy).
--   * Migration numbering: P1 V2/V3, Academic V4/V5, Content V6/V7, so Assessment is V9/V10
--     (V8 is the delivery_policy prerequisite).
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- blueprint
-- ---------------------------------------------------------------------------
CREATE TABLE blueprint (
    id          UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    CONSTRAINT pk_blueprint PRIMARY KEY (id),
    CONSTRAINT uk_blueprint_code UNIQUE (code),
    CONSTRAINT ck_blueprint_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

-- ---------------------------------------------------------------------------
-- blueprint_version
-- ---------------------------------------------------------------------------
CREATE TABLE blueprint_version (
    id            UUID         NOT NULL,
    blueprint_id  UUID         NOT NULL,
    version       INTEGER      NOT NULL,
    configuration JSONB,
    is_published  BOOLEAN      NOT NULL,
    published_by  UUID,
    published_on  TIMESTAMPTZ,
    created_by    UUID,
    created_on    TIMESTAMPTZ,
    updated_by    UUID,
    updated_on    TIMESTAMPTZ,
    status        VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_blueprint_version PRIMARY KEY (id),
    CONSTRAINT uk_blueprint_version_blueprint_id_version UNIQUE (blueprint_id, version),
    CONSTRAINT ck_blueprint_version_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_blueprint_version_blueprint FOREIGN KEY (blueprint_id) REFERENCES blueprint (id)
);

CREATE INDEX idx_blueprint_version_blueprint_id ON blueprint_version (blueprint_id);

-- ---------------------------------------------------------------------------
-- assessment
-- ---------------------------------------------------------------------------
CREATE TABLE assessment (
    id          UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    CONSTRAINT pk_assessment PRIMARY KEY (id),
    CONSTRAINT uk_assessment_code UNIQUE (code),
    CONSTRAINT ck_assessment_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

-- ---------------------------------------------------------------------------
-- assessment_version
-- ---------------------------------------------------------------------------
CREATE TABLE assessment_version (
    id                           UUID          NOT NULL,
    assessment_id                UUID          NOT NULL,
    blueprint_version_id         UUID          NOT NULL,
    version                      INTEGER       NOT NULL,
    total_questions              INTEGER,
    total_marks                  NUMERIC(8, 2),
    duration_minutes             INTEGER,
    engine_type                  VARCHAR(20),
    engine_configuration_version INTEGER,
    delivery_policy_id           UUID,
    is_published                 BOOLEAN       NOT NULL,
    published_by                 UUID,
    published_on                 TIMESTAMPTZ,
    created_by                   UUID,
    created_on                   TIMESTAMPTZ,
    updated_by                   UUID,
    updated_on                   TIMESTAMPTZ,
    status                       VARCHAR(20)   NOT NULL,
    CONSTRAINT pk_assessment_version PRIMARY KEY (id),
    CONSTRAINT uk_assessment_version_assessment_id_version UNIQUE (assessment_id, version),
    CONSTRAINT ck_assessment_version_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_assessment_version_engine_type
        CHECK (engine_type IS NULL OR engine_type IN ('TCS_ION', 'IBPS', 'NTA', 'UPSC', 'RAILWAY', 'CUSTOM')),
    CONSTRAINT fk_assessment_version_assessment FOREIGN KEY (assessment_id) REFERENCES assessment (id),
    CONSTRAINT fk_assessment_version_blueprint_version FOREIGN KEY (blueprint_version_id) REFERENCES blueprint_version (id),
    CONSTRAINT fk_assessment_version_delivery_policy FOREIGN KEY (delivery_policy_id) REFERENCES delivery_policy (id)
);

CREATE INDEX idx_assessment_version_assessment_id ON assessment_version (assessment_id);
CREATE INDEX idx_assessment_version_blueprint_version_id ON assessment_version (blueprint_version_id);
CREATE INDEX idx_assessment_version_engine_type ON assessment_version (engine_type);
CREATE INDEX idx_assessment_version_delivery_policy_id ON assessment_version (delivery_policy_id);

-- ---------------------------------------------------------------------------
-- assessment_question
-- ---------------------------------------------------------------------------
CREATE TABLE assessment_question (
    id                    UUID         NOT NULL,
    assessment_version_id UUID         NOT NULL,
    question_version_id   UUID         NOT NULL,
    display_order         INTEGER      NOT NULL,
    section_name          VARCHAR(150),
    marks                 NUMERIC(8, 2),
    negative_marks        NUMERIC(8, 2),
    created_by            UUID,
    created_on            TIMESTAMPTZ,
    updated_by            UUID,
    updated_on            TIMESTAMPTZ,
    status                VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_assessment_question PRIMARY KEY (id),
    CONSTRAINT uk_assessment_question_version_question UNIQUE (assessment_version_id, question_version_id),
    CONSTRAINT ck_assessment_question_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_assessment_question_assessment_version FOREIGN KEY (assessment_version_id) REFERENCES assessment_version (id),
    CONSTRAINT fk_assessment_question_question_version FOREIGN KEY (question_version_id) REFERENCES question_version (id)
);

CREATE INDEX idx_assessment_question_assessment_version_id ON assessment_question (assessment_version_id);
CREATE INDEX idx_assessment_question_question_version_id ON assessment_question (question_version_id);

-- ---------------------------------------------------------------------------
-- assessment_selection_rule
-- ---------------------------------------------------------------------------
CREATE TABLE assessment_selection_rule (
    id                          UUID         NOT NULL,
    assessment_version_id       UUID         NOT NULL,
    section_name                VARCHAR(150) NOT NULL,
    target_count                INTEGER      NOT NULL,
    rule_configuration          JSONB,
    display_order               INTEGER,
    materialized_on             TIMESTAMPTZ,
    materialized_question_count INTEGER,
    created_by                  UUID,
    created_on                  TIMESTAMPTZ,
    updated_by                  UUID,
    updated_on                  TIMESTAMPTZ,
    status                      VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_assessment_selection_rule PRIMARY KEY (id),
    CONSTRAINT uk_assessment_selection_rule_version_section UNIQUE (assessment_version_id, section_name),
    CONSTRAINT ck_assessment_selection_rule_target_count CHECK (target_count > 0),
    CONSTRAINT ck_assessment_selection_rule_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_assessment_selection_rule_assessment_version FOREIGN KEY (assessment_version_id) REFERENCES assessment_version (id)
);

CREATE INDEX idx_assessment_selection_rule_version ON assessment_selection_rule (assessment_version_id);

-- ---------------------------------------------------------------------------
-- assessment_schedule
-- ---------------------------------------------------------------------------
CREATE TABLE assessment_schedule (
    id                    UUID        NOT NULL,
    assessment_version_id UUID        NOT NULL,
    mode                  VARCHAR(20) NOT NULL,
    start_time            TIMESTAMPTZ NOT NULL,
    end_time              TIMESTAMPTZ NOT NULL,
    status                VARCHAR(20) NOT NULL,
    created_by            UUID,
    created_on            TIMESTAMPTZ,
    updated_by            UUID,
    updated_on            TIMESTAMPTZ,
    CONSTRAINT pk_assessment_schedule PRIMARY KEY (id),
    CONSTRAINT ck_assessment_schedule_mode CHECK (mode IN ('PRACTICE', 'EXAM')),
    CONSTRAINT ck_assessment_schedule_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_assessment_schedule_assessment_version FOREIGN KEY (assessment_version_id) REFERENCES assessment_version (id)
);

CREATE INDEX idx_assessment_schedule_assessment_version_id ON assessment_schedule (assessment_version_id);
CREATE INDEX idx_assessment_schedule_start_time ON assessment_schedule (start_time);
CREATE INDEX idx_assessment_schedule_status ON assessment_schedule (status);

-- ---------------------------------------------------------------------------
-- assessment_collection
-- ---------------------------------------------------------------------------
CREATE TABLE assessment_collection (
    id          UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    CONSTRAINT pk_assessment_collection PRIMARY KEY (id),
    CONSTRAINT uk_assessment_collection_code UNIQUE (code),
    CONSTRAINT ck_assessment_collection_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

-- ---------------------------------------------------------------------------
-- collection_assessment
-- ---------------------------------------------------------------------------
CREATE TABLE collection_assessment (
    id            UUID        NOT NULL,
    collection_id UUID        NOT NULL,
    assessment_id UUID        NOT NULL,
    created_by    UUID,
    created_on    TIMESTAMPTZ,
    updated_by    UUID,
    updated_on    TIMESTAMPTZ,
    status        VARCHAR(20) NOT NULL,
    CONSTRAINT pk_collection_assessment PRIMARY KEY (id),
    CONSTRAINT uk_collection_assessment_collection_id_assessment_id UNIQUE (collection_id, assessment_id),
    CONSTRAINT ck_collection_assessment_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_collection_assessment_collection FOREIGN KEY (collection_id) REFERENCES assessment_collection (id),
    CONSTRAINT fk_collection_assessment_assessment FOREIGN KEY (assessment_id) REFERENCES assessment (id)
);

CREATE INDEX idx_collection_assessment_collection_id ON collection_assessment (collection_id);
CREATE INDEX idx_collection_assessment_assessment_id ON collection_assessment (assessment_id);
