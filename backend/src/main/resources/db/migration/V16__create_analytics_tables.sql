-- ---------------------------------------------------------------------------
-- V16 - Analytics schema (P8-02)
--
-- Creates the five frozen Analytics tables from specs/17-P8-Analytics-V1.5.md section 6 and
-- specs/04-Database-Schema-V1.5.md section 31:
--
--   student_analytics, assessment_analytics, question_analytics, topic_analytics,
--   organization_analytics
--
-- Design notes:
--   * Columns, types, unique constraints and the named index are taken verbatim from the frozen
--     schema. No table or column is invented (§1960, §1961).
--   * Analytics tables are derived, recalculable, read-heavy and non-authoritative; PostgreSQL
--     transactional data remains the source of truth (AGENTS.md rule 7).
--   * There is no time-series/event/fact/dimension table and no materialized view (§5, §127).
--   * Each table has exactly one current aggregate row per entity, so UNIQUE(entity_id) provides
--     upsert semantics (§96). There are no audit columns and no lifecycle `status` column.
--   * Migration numbering: earlier phases used V2-V15, so Analytics is V16/V17. The schema document
--     says the grouping is "Recommended" and numbering may differ (GAP-016/020/039/045/061/077).
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- student_analytics
-- ---------------------------------------------------------------------------
CREATE TABLE student_analytics (
    id                    UUID          NOT NULL,
    user_id               UUID          NOT NULL,
    assessments_attempted INTEGER,
    average_score         NUMERIC(8, 2),
    accuracy              NUMERIC(8, 2),
    average_time          NUMERIC(10, 2),
    last_calculated_on    TIMESTAMPTZ,
    CONSTRAINT pk_student_analytics PRIMARY KEY (id),
    CONSTRAINT uk_student_analytics_user_id UNIQUE (user_id),
    CONSTRAINT fk_student_analytics_user FOREIGN KEY (user_id) REFERENCES "user" (id)
);

CREATE INDEX idx_student_analytics_user_id ON student_analytics (user_id);

-- ---------------------------------------------------------------------------
-- assessment_analytics
-- ---------------------------------------------------------------------------
CREATE TABLE assessment_analytics (
    id                 UUID          NOT NULL,
    assessment_id      UUID          NOT NULL,
    total_attempts     INTEGER,
    average_score      NUMERIC(8, 2),
    highest_score      NUMERIC(8, 2),
    lowest_score       NUMERIC(8, 2),
    last_calculated_on TIMESTAMPTZ,
    CONSTRAINT pk_assessment_analytics PRIMARY KEY (id),
    CONSTRAINT uk_assessment_analytics_assessment_id UNIQUE (assessment_id),
    CONSTRAINT fk_assessment_analytics_assessment FOREIGN KEY (assessment_id) REFERENCES assessment (id)
);

-- ---------------------------------------------------------------------------
-- question_analytics
-- ---------------------------------------------------------------------------
CREATE TABLE question_analytics (
    id                 UUID          NOT NULL,
    question_id        UUID          NOT NULL,
    total_attempts     INTEGER,
    correct_attempts   INTEGER,
    incorrect_attempts INTEGER,
    average_time       NUMERIC(10, 2),
    difficulty_index   NUMERIC(8, 4),
    last_calculated_on TIMESTAMPTZ,
    CONSTRAINT pk_question_analytics PRIMARY KEY (id),
    CONSTRAINT uk_question_analytics_question_id UNIQUE (question_id),
    CONSTRAINT fk_question_analytics_question FOREIGN KEY (question_id) REFERENCES question (id)
);

-- ---------------------------------------------------------------------------
-- topic_analytics
-- ---------------------------------------------------------------------------
CREATE TABLE topic_analytics (
    id                 UUID          NOT NULL,
    topic_id           UUID          NOT NULL,
    total_attempts     INTEGER,
    average_score      NUMERIC(8, 2),
    accuracy           NUMERIC(8, 2),
    last_calculated_on TIMESTAMPTZ,
    CONSTRAINT pk_topic_analytics PRIMARY KEY (id),
    CONSTRAINT uk_topic_analytics_topic_id UNIQUE (topic_id),
    CONSTRAINT fk_topic_analytics_topic FOREIGN KEY (topic_id) REFERENCES topic (id)
);

-- ---------------------------------------------------------------------------
-- organization_analytics
-- ---------------------------------------------------------------------------
CREATE TABLE organization_analytics (
    id                 UUID        NOT NULL,
    organization_id    UUID        NOT NULL,
    total_students     INTEGER,
    total_assessments  INTEGER,
    total_attempts     INTEGER,
    active_users       INTEGER,
    last_calculated_on TIMESTAMPTZ,
    CONSTRAINT pk_organization_analytics PRIMARY KEY (id),
    CONSTRAINT uk_organization_analytics_organization_id UNIQUE (organization_id),
    CONSTRAINT fk_organization_analytics_organization FOREIGN KEY (organization_id) REFERENCES organization (id)
);
