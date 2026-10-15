-- ---------------------------------------------------------------------------
-- V14 - Evaluation schema (P6-02/P6-03)
--
-- Creates the five frozen Evaluation tables from specs/15-P6-Evaluation-V1.5.md section 5 and
-- specs/04-Database-Schema-V1.5.md section 30:
--
--   answer_key, answer_key_detail, evaluation, result, scorecard
--
-- Design notes:
--   * Columns, types, keys, unique constraints, indexes, and CHECK constraints are taken
--     verbatim from the frozen schema. No table or column is invented (§165).
--   * Status columns use controlled VARCHAR values with CHECK constraints (the project enum
--     strategy, 04-Schema §10).
--   * evaluation carries the lifecycle vocabulary PENDING/IN_PROGRESS/COMPLETED/FAILED/SUPERSEDED
--     in `status`; result carries CALCULATED/READY_FOR_PUBLICATION/PUBLISHED/SUPERSEDED in
--     `result_status` (specs/15-P6 §6, §7).
--   * answer_key_detail is the ONLY authoritative correctness source (AGENTS.md rule 19); the
--     outcome/answer CHECK enforces the frozen invariants:
--       NORMAL -> at least one of correct_option_id / correct_answer_text
--       BONUS or DROPPED -> both null
--   * The partial unique index enforces at most one current (non-superseded) Evaluation per
--     attempt (specs/15-P6 §44, §47).
--   * Migration numbering: earlier phases used V2-V13, so Evaluation is V14/V15. The schema
--     document states the grouping is "Recommended" and numbering may differ (GAP-016/020/039/045/061).
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- answer_key
-- ---------------------------------------------------------------------------
CREATE TABLE answer_key (
    id                    UUID        NOT NULL,
    assessment_version_id UUID        NOT NULL,
    version               INTEGER     NOT NULL,
    status                VARCHAR(20) NOT NULL,
    is_published          BOOLEAN     NOT NULL,
    published_by          UUID,
    published_on          TIMESTAMPTZ,
    created_by            UUID,
    created_on            TIMESTAMPTZ,
    updated_by            UUID,
    updated_on            TIMESTAMPTZ,
    CONSTRAINT pk_answer_key PRIMARY KEY (id),
    CONSTRAINT uk_answer_key_assessment_version_id_version UNIQUE (assessment_version_id, version),
    CONSTRAINT ck_answer_key_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_answer_key_assessment_version FOREIGN KEY (assessment_version_id) REFERENCES assessment_version (id)
);

CREATE INDEX idx_answer_key_assessment_version_id ON answer_key (assessment_version_id);

-- ---------------------------------------------------------------------------
-- answer_key_detail
-- ---------------------------------------------------------------------------
CREATE TABLE answer_key_detail (
    id                     UUID          NOT NULL,
    answer_key_id          UUID          NOT NULL,
    question_version_id    UUID          NOT NULL,
    correct_option_id      UUID,
    correct_answer_text    TEXT,
    question_outcome       VARCHAR(20)   NOT NULL,
    awarded_marks          NUMERIC(8, 2),
    awarded_negative_marks NUMERIC(8, 2),
    remarks                TEXT,
    created_by             UUID,
    created_on             TIMESTAMPTZ,
    updated_by             UUID,
    updated_on             TIMESTAMPTZ,
    status                 VARCHAR(20)   NOT NULL,
    CONSTRAINT pk_answer_key_detail PRIMARY KEY (id),
    CONSTRAINT uk_answer_key_detail_answer_key_id_question_version_id UNIQUE (answer_key_id, question_version_id),
    CONSTRAINT ck_answer_key_detail_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_answer_key_detail_question_outcome CHECK (question_outcome IN ('NORMAL', 'BONUS', 'DROPPED')),
    CONSTRAINT ck_answer_key_detail_answer CHECK (
        (question_outcome = 'NORMAL' AND (correct_option_id IS NOT NULL OR correct_answer_text IS NOT NULL))
        OR (question_outcome IN ('BONUS', 'DROPPED') AND correct_option_id IS NULL AND correct_answer_text IS NULL)
    ),
    CONSTRAINT fk_answer_key_detail_answer_key FOREIGN KEY (answer_key_id) REFERENCES answer_key (id),
    CONSTRAINT fk_answer_key_detail_question_version FOREIGN KEY (question_version_id) REFERENCES question_version (id),
    CONSTRAINT fk_answer_key_detail_correct_option FOREIGN KEY (correct_option_id) REFERENCES question_option (id)
);

CREATE INDEX idx_answer_key_detail_answer_key_id ON answer_key_detail (answer_key_id);
CREATE INDEX idx_answer_key_detail_question_version_id ON answer_key_detail (question_version_id);
CREATE INDEX idx_answer_key_detail_correct_option_id ON answer_key_detail (correct_option_id);

-- ---------------------------------------------------------------------------
-- evaluation
-- ---------------------------------------------------------------------------
CREATE TABLE evaluation (
    id                UUID        NOT NULL,
    attempt_id        UUID        NOT NULL,
    answer_key_id     UUID        NOT NULL,
    version           INTEGER     NOT NULL,
    superseded_by_id  UUID,
    superseded_on     TIMESTAMPTZ,
    superseded_reason TEXT,
    evaluated_on      TIMESTAMPTZ,
    status            VARCHAR(20) NOT NULL,
    created_by        UUID,
    created_on        TIMESTAMPTZ,
    updated_by        UUID,
    updated_on        TIMESTAMPTZ,
    CONSTRAINT pk_evaluation PRIMARY KEY (id),
    CONSTRAINT uk_evaluation_attempt_id_version UNIQUE (attempt_id, version),
    CONSTRAINT ck_evaluation_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'SUPERSEDED')),
    CONSTRAINT fk_evaluation_attempt FOREIGN KEY (attempt_id) REFERENCES student_attempt (id),
    CONSTRAINT fk_evaluation_answer_key FOREIGN KEY (answer_key_id) REFERENCES answer_key (id),
    CONSTRAINT fk_evaluation_superseded_by FOREIGN KEY (superseded_by_id) REFERENCES evaluation (id)
);

CREATE INDEX idx_evaluation_attempt_id ON evaluation (attempt_id);
CREATE INDEX idx_evaluation_answer_key_id ON evaluation (answer_key_id);
CREATE INDEX idx_evaluation_current_lookup ON evaluation (attempt_id, superseded_by_id);

-- At most one current (non-superseded) Evaluation per attempt (specs/15-P6 §44).
CREATE UNIQUE INDEX uk_evaluation_current_attempt ON evaluation (attempt_id) WHERE superseded_by_id IS NULL;

-- ---------------------------------------------------------------------------
-- result
-- ---------------------------------------------------------------------------
CREATE TABLE result (
    id            UUID          NOT NULL,
    evaluation_id UUID          NOT NULL,
    obtained_marks NUMERIC(8, 2),
    percentage    NUMERIC(8, 2),
    rank          INTEGER,
    percentile    NUMERIC(8, 4),
    result_status VARCHAR(30)   NOT NULL,
    superseded_on TIMESTAMPTZ,
    created_by    UUID,
    created_on    TIMESTAMPTZ,
    updated_by    UUID,
    updated_on    TIMESTAMPTZ,
    CONSTRAINT pk_result PRIMARY KEY (id),
    CONSTRAINT uk_result_evaluation_id UNIQUE (evaluation_id),
    CONSTRAINT ck_result_result_status
        CHECK (result_status IN ('CALCULATED', 'READY_FOR_PUBLICATION', 'PUBLISHED', 'SUPERSEDED')),
    CONSTRAINT fk_result_evaluation FOREIGN KEY (evaluation_id) REFERENCES evaluation (id)
);

CREATE INDEX idx_result_rank ON result (rank);
CREATE INDEX idx_result_result_status ON result (result_status);
CREATE INDEX idx_result_obtained_marks ON result (obtained_marks);

-- ---------------------------------------------------------------------------
-- scorecard
-- ---------------------------------------------------------------------------
CREATE TABLE scorecard (
    id           UUID        NOT NULL,
    result_id    UUID        NOT NULL,
    generated_on TIMESTAMPTZ,
    file_path    TEXT,
    created_by   UUID,
    created_on   TIMESTAMPTZ,
    updated_by   UUID,
    updated_on   TIMESTAMPTZ,
    status       VARCHAR(20) NOT NULL,
    CONSTRAINT pk_scorecard PRIMARY KEY (id),
    CONSTRAINT uk_scorecard_result_id UNIQUE (result_id),
    CONSTRAINT ck_scorecard_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_scorecard_result FOREIGN KEY (result_id) REFERENCES result (id)
);

CREATE INDEX idx_scorecard_result_id ON scorecard (result_id);
