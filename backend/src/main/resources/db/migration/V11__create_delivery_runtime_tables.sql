-- ---------------------------------------------------------------------------
-- V11 - Delivery runtime schema (P5-02)
--
-- Creates the five frozen Delivery runtime tables from
-- specs/14-P5-Delivery-V1.5.md section 7-11 and specs/04-Database-Schema-V1.5.md section 29.
--
--   student_attempt, assessment_session, attempt_question, attempt_question_option,
--   student_response
--
-- delivery_policy already exists (V8). No new table is invented (P5 §155).
--
-- Design notes:
--   * attempt_question and attempt_question_option are the immutable snapshot; rows are immutable
--     once student_attempt.snapshot_status = READY (enforced by the service, not the schema).
--   * student_attempt has NO generic `status` column; it uses attempt_status and snapshot_status.
--   * Stale-write protection on student_response uses HTTP conditional requests (the ETag is
--     derived from updated_on); no response_version column is added.
--   * Status columns use CHECK-constrained VARCHAR (the project enum strategy).
--   * Creation order follows foreign keys: student_attempt must exist before assessment_session.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- student_attempt
-- ---------------------------------------------------------------------------
CREATE TABLE student_attempt (
    id                     UUID          NOT NULL,
    user_id                UUID          NOT NULL,
    assessment_version_id  UUID          NOT NULL,
    assessment_schedule_id UUID          NOT NULL,
    attempt_mode           VARCHAR(20)   NOT NULL,
    attempt_status         VARCHAR(20)   NOT NULL,
    snapshot_status        VARCHAR(20)   NOT NULL,
    snapshot_prepared_on   TIMESTAMPTZ,
    started_on             TIMESTAMPTZ,
    submitted_on           TIMESTAMPTZ,
    total_score            NUMERIC(8, 2),
    created_by             UUID,
    created_on             TIMESTAMPTZ,
    updated_by             UUID,
    updated_on             TIMESTAMPTZ,
    CONSTRAINT pk_student_attempt PRIMARY KEY (id),
    CONSTRAINT ck_student_attempt_mode CHECK (attempt_mode IN ('PRACTICE', 'EXAM')),
    CONSTRAINT ck_student_attempt_attempt_status
        CHECK (attempt_status IN ('NOT_STARTED', 'IN_PROGRESS', 'SUBMITTED', 'EVALUATED')),
    CONSTRAINT ck_student_attempt_snapshot_status
        CHECK (snapshot_status IN ('PENDING', 'PREPARING', 'READY', 'FAILED')),
    CONSTRAINT fk_student_attempt_user FOREIGN KEY (user_id) REFERENCES "user" (id),
    CONSTRAINT fk_student_attempt_assessment_version FOREIGN KEY (assessment_version_id) REFERENCES assessment_version (id),
    CONSTRAINT fk_student_attempt_schedule FOREIGN KEY (assessment_schedule_id) REFERENCES assessment_schedule (id)
);

CREATE INDEX idx_student_attempt_user_id ON student_attempt (user_id);
CREATE INDEX idx_student_attempt_assessment_version_id ON student_attempt (assessment_version_id);
CREATE INDEX idx_student_attempt_assessment_schedule_id ON student_attempt (assessment_schedule_id);
CREATE INDEX idx_student_attempt_attempt_status ON student_attempt (attempt_status);
CREATE INDEX idx_student_attempt_snapshot_status ON student_attempt (snapshot_status);
CREATE INDEX idx_student_attempt_schedule_snapshot_status ON student_attempt (assessment_schedule_id, snapshot_status);
CREATE INDEX idx_student_attempt_started_on ON student_attempt (started_on);

-- ---------------------------------------------------------------------------
-- assessment_session
-- ---------------------------------------------------------------------------
CREATE TABLE assessment_session (
    id                     UUID        NOT NULL,
    user_id                UUID        NOT NULL,
    assessment_schedule_id UUID        NOT NULL,
    attempt_id             UUID        NOT NULL,
    session_status         VARCHAR(20) NOT NULL,
    started_on             TIMESTAMPTZ,
    last_activity_on       TIMESTAMPTZ,
    ended_on               TIMESTAMPTZ,
    CONSTRAINT pk_assessment_session PRIMARY KEY (id),
    CONSTRAINT ck_assessment_session_status CHECK (session_status IN ('ACTIVE', 'EXPIRED', 'LOGGED_OUT')),
    CONSTRAINT fk_assessment_session_user FOREIGN KEY (user_id) REFERENCES "user" (id),
    CONSTRAINT fk_assessment_session_schedule FOREIGN KEY (assessment_schedule_id) REFERENCES assessment_schedule (id),
    CONSTRAINT fk_assessment_session_attempt FOREIGN KEY (attempt_id) REFERENCES student_attempt (id)
);

CREATE INDEX idx_assessment_session_user_id ON assessment_session (user_id);
CREATE INDEX idx_assessment_session_schedule_id ON assessment_session (assessment_schedule_id);
CREATE INDEX idx_assessment_session_session_status ON assessment_session (session_status);
CREATE INDEX idx_assessment_session_last_activity_on ON assessment_session (last_activity_on);

-- ---------------------------------------------------------------------------
-- attempt_question
-- ---------------------------------------------------------------------------
CREATE TABLE attempt_question (
    id                  UUID          NOT NULL,
    attempt_id          UUID          NOT NULL,
    question_version_id UUID          NOT NULL,
    display_order       INTEGER       NOT NULL,
    section_name        VARCHAR(150),
    marks               NUMERIC(8, 2),
    negative_marks      NUMERIC(8, 2),
    created_on          TIMESTAMPTZ,
    CONSTRAINT pk_attempt_question PRIMARY KEY (id),
    CONSTRAINT uk_attempt_question_attempt_question_version UNIQUE (attempt_id, question_version_id),
    CONSTRAINT uk_attempt_question_attempt_display_order UNIQUE (attempt_id, display_order),
    CONSTRAINT fk_attempt_question_attempt FOREIGN KEY (attempt_id) REFERENCES student_attempt (id),
    CONSTRAINT fk_attempt_question_question_version FOREIGN KEY (question_version_id) REFERENCES question_version (id)
);

CREATE INDEX idx_attempt_question_attempt_id ON attempt_question (attempt_id);
CREATE INDEX idx_attempt_question_question_version_id ON attempt_question (question_version_id);
CREATE INDEX idx_attempt_question_attempt_order ON attempt_question (attempt_id, display_order);

-- ---------------------------------------------------------------------------
-- attempt_question_option
-- ---------------------------------------------------------------------------
CREATE TABLE attempt_question_option (
    id                  UUID    NOT NULL,
    attempt_question_id UUID    NOT NULL,
    question_option_id  UUID    NOT NULL,
    display_order       INTEGER NOT NULL,
    CONSTRAINT pk_attempt_question_option PRIMARY KEY (id),
    CONSTRAINT uk_attempt_question_option_question_option UNIQUE (attempt_question_id, question_option_id),
    CONSTRAINT uk_attempt_question_option_display_order UNIQUE (attempt_question_id, display_order),
    CONSTRAINT fk_attempt_question_option_attempt_question FOREIGN KEY (attempt_question_id) REFERENCES attempt_question (id),
    CONSTRAINT fk_attempt_question_option_question_option FOREIGN KEY (question_option_id) REFERENCES question_option (id)
);

CREATE INDEX idx_attempt_question_option_attempt_question_id ON attempt_question_option (attempt_question_id);
CREATE INDEX idx_attempt_question_option_question_option_id ON attempt_question_option (question_option_id);

-- ---------------------------------------------------------------------------
-- student_response
-- ---------------------------------------------------------------------------
CREATE TABLE student_response (
    id                  UUID        NOT NULL,
    attempt_id          UUID        NOT NULL,
    question_version_id UUID        NOT NULL,
    response            TEXT,
    time_spent_seconds  INTEGER,
    marked_for_review   BOOLEAN,
    visited             BOOLEAN,
    created_by          UUID,
    created_on          TIMESTAMPTZ,
    updated_by          UUID,
    updated_on          TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL,
    CONSTRAINT pk_student_response PRIMARY KEY (id),
    CONSTRAINT uk_student_response_attempt_question UNIQUE (attempt_id, question_version_id),
    CONSTRAINT ck_student_response_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_student_response_attempt FOREIGN KEY (attempt_id) REFERENCES student_attempt (id),
    CONSTRAINT fk_student_response_question_version FOREIGN KEY (question_version_id) REFERENCES question_version (id)
);

CREATE INDEX idx_student_response_attempt_id ON student_response (attempt_id);
CREATE INDEX idx_student_response_question_version_id ON student_response (question_version_id);
