-- ---------------------------------------------------------------------------
-- V6 - Content schema (P3-02)
--
-- Creates the nine frozen Content tables from specs/12-P3-Content-V1.5.md section 10
-- and specs/04-Database-Schema-V1.5.md section 27.
--
--   question_template, question, question_version, question_option, asset,
--   explanation, hint, previous_exam_history, question_tag
--
-- Design notes:
--   * Columns, types, keys, unique constraints, indexes, and the review_status
--     CHECK are taken verbatim from the frozen schema. No table or column is
--     invented.
--   * question_version has NO status and NO audit columns (frozen schema),
--     uses is_published + review_status.
--   * question_option.is_correct is server-side evaluation data, never returned
--     in student payloads.
--   * Published QuestionVersions are immutable; that is enforced by the service,
--     not the schema.
--   * Migration numbering: P1 used V2/V3 and Academic V4/V5, so Content is
--     V6/V7 rather than the schema document's recommended V4/V13 (GAP-016/GAP-020).
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- question_template
-- ---------------------------------------------------------------------------
CREATE TABLE question_template (
    id            UUID         NOT NULL,
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   TEXT,
    configuration JSONB,
    status        VARCHAR(20)  NOT NULL,
    created_by    UUID,
    created_on    TIMESTAMPTZ,
    updated_by    UUID,
    updated_on    TIMESTAMPTZ,
    CONSTRAINT pk_question_template PRIMARY KEY (id),
    CONSTRAINT uk_question_template_code UNIQUE (code),
    CONSTRAINT ck_question_template_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

-- ---------------------------------------------------------------------------
-- question
-- ---------------------------------------------------------------------------
CREATE TABLE question (
    id           UUID        NOT NULL,
    template_id  UUID        NOT NULL,
    exam_id      UUID        NOT NULL,
    subject_id   UUID        NOT NULL,
    chapter_id   UUID,
    topic_id     UUID,
    sub_topic_id UUID,
    status       VARCHAR(20) NOT NULL,
    created_by   UUID,
    created_on   TIMESTAMPTZ,
    updated_by   UUID,
    updated_on   TIMESTAMPTZ,
    CONSTRAINT pk_question PRIMARY KEY (id),
    CONSTRAINT ck_question_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_question_template FOREIGN KEY (template_id) REFERENCES question_template (id),
    CONSTRAINT fk_question_exam FOREIGN KEY (exam_id) REFERENCES exam (id),
    CONSTRAINT fk_question_subject FOREIGN KEY (subject_id) REFERENCES subject (id),
    CONSTRAINT fk_question_chapter FOREIGN KEY (chapter_id) REFERENCES chapter (id),
    CONSTRAINT fk_question_topic FOREIGN KEY (topic_id) REFERENCES topic (id),
    CONSTRAINT fk_question_sub_topic FOREIGN KEY (sub_topic_id) REFERENCES sub_topic (id)
);

CREATE INDEX idx_question_template_id ON question (template_id);
CREATE INDEX idx_question_exam_id ON question (exam_id);
CREATE INDEX idx_question_subject_id ON question (subject_id);
CREATE INDEX idx_question_chapter_id ON question (chapter_id);
CREATE INDEX idx_question_topic_id ON question (topic_id);
CREATE INDEX idx_question_sub_topic_id ON question (sub_topic_id);

-- ---------------------------------------------------------------------------
-- question_version
-- ---------------------------------------------------------------------------
CREATE TABLE question_version (
    id                        UUID          NOT NULL,
    question_id               UUID          NOT NULL,
    version                   INTEGER       NOT NULL,
    title                     VARCHAR(255),
    content                   TEXT,
    marks                     NUMERIC(8, 2),
    negative_marks            NUMERIC(8, 2),
    difficulty                VARCHAR(20),
    language_id               UUID          NOT NULL,
    is_published              BOOLEAN       NOT NULL,
    published_by              UUID,
    published_on              TIMESTAMPTZ,
    review_status             VARCHAR(20)   NOT NULL,
    submitted_for_review_by   UUID,
    submitted_for_review_on   TIMESTAMPTZ,
    reviewed_by               UUID,
    reviewed_on               TIMESTAMPTZ,
    review_remarks            TEXT,
    CONSTRAINT pk_question_version PRIMARY KEY (id),
    CONSTRAINT uk_question_version_question_id_version UNIQUE (question_id, version),
    CONSTRAINT ck_question_version_review_status
        CHECK (review_status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'PUBLISHED')),
    CONSTRAINT fk_question_version_question FOREIGN KEY (question_id) REFERENCES question (id),
    CONSTRAINT fk_question_version_language FOREIGN KEY (language_id) REFERENCES language (id)
);

CREATE INDEX idx_question_version_question_id ON question_version (question_id);
CREATE INDEX idx_question_version_language_id ON question_version (language_id);
CREATE INDEX idx_question_version_review_status ON question_version (review_status);

-- ---------------------------------------------------------------------------
-- question_option
-- ---------------------------------------------------------------------------
CREATE TABLE question_option (
    id                  UUID        NOT NULL,
    question_version_id UUID        NOT NULL,
    option_key          VARCHAR(20) NOT NULL,
    content             TEXT,
    display_order       INTEGER     NOT NULL,
    is_correct          BOOLEAN     NOT NULL,
    status              VARCHAR(20) NOT NULL,
    created_by          UUID,
    created_on          TIMESTAMPTZ,
    updated_by          UUID,
    updated_on          TIMESTAMPTZ,
    CONSTRAINT pk_question_option PRIMARY KEY (id),
    CONSTRAINT uk_question_option_question_version_id_option_key UNIQUE (question_version_id, option_key),
    CONSTRAINT uk_question_option_question_version_id_display_order UNIQUE (question_version_id, display_order),
    CONSTRAINT ck_question_option_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_question_option_question_version FOREIGN KEY (question_version_id) REFERENCES question_version (id)
);

CREATE INDEX idx_question_option_question_version_id ON question_option (question_version_id);

-- ---------------------------------------------------------------------------
-- asset
-- ---------------------------------------------------------------------------
CREATE TABLE asset (
    id                  UUID        NOT NULL,
    question_version_id UUID        NOT NULL,
    file_name           VARCHAR(255),
    file_path           TEXT,
    file_type           VARCHAR(100),
    created_by          UUID,
    created_on          TIMESTAMPTZ,
    updated_by          UUID,
    updated_on          TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL,
    CONSTRAINT pk_asset PRIMARY KEY (id),
    CONSTRAINT ck_asset_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_asset_question_version FOREIGN KEY (question_version_id) REFERENCES question_version (id)
);

CREATE INDEX idx_asset_question_version_id ON asset (question_version_id);

-- ---------------------------------------------------------------------------
-- explanation
-- ---------------------------------------------------------------------------
CREATE TABLE explanation (
    id                  UUID        NOT NULL,
    question_version_id UUID        NOT NULL,
    content             TEXT,
    created_by          UUID,
    created_on          TIMESTAMPTZ,
    updated_by          UUID,
    updated_on          TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL,
    CONSTRAINT pk_explanation PRIMARY KEY (id),
    CONSTRAINT ck_explanation_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_explanation_question_version FOREIGN KEY (question_version_id) REFERENCES question_version (id)
);

CREATE INDEX idx_explanation_question_version_id ON explanation (question_version_id);

-- ---------------------------------------------------------------------------
-- hint
-- ---------------------------------------------------------------------------
CREATE TABLE hint (
    id                  UUID        NOT NULL,
    question_version_id UUID        NOT NULL,
    content             TEXT,
    created_by          UUID,
    created_on          TIMESTAMPTZ,
    updated_by          UUID,
    updated_on          TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL,
    CONSTRAINT pk_hint PRIMARY KEY (id),
    CONSTRAINT ck_hint_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_hint_question_version FOREIGN KEY (question_version_id) REFERENCES question_version (id)
);

CREATE INDEX idx_hint_question_version_id ON hint (question_version_id);

-- ---------------------------------------------------------------------------
-- previous_exam_history
-- ---------------------------------------------------------------------------
CREATE TABLE previous_exam_history (
    id          UUID         NOT NULL,
    question_id UUID         NOT NULL,
    exam_id     UUID         NOT NULL,
    year        INTEGER,
    shift       VARCHAR(50),
    source      VARCHAR(100),
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    status      VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_previous_exam_history PRIMARY KEY (id),
    CONSTRAINT ck_previous_exam_history_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_previous_exam_history_question FOREIGN KEY (question_id) REFERENCES question (id),
    CONSTRAINT fk_previous_exam_history_exam FOREIGN KEY (exam_id) REFERENCES exam (id)
);

CREATE INDEX idx_previous_exam_history_question_id ON previous_exam_history (question_id);
CREATE INDEX idx_previous_exam_history_exam_id ON previous_exam_history (exam_id);

-- ---------------------------------------------------------------------------
-- question_tag
-- ---------------------------------------------------------------------------
CREATE TABLE question_tag (
    id          UUID        NOT NULL,
    question_id UUID        NOT NULL,
    tag_id      UUID        NOT NULL,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    status      VARCHAR(20) NOT NULL,
    CONSTRAINT pk_question_tag PRIMARY KEY (id),
    CONSTRAINT uk_question_tag_question_id_tag_id UNIQUE (question_id, tag_id),
    CONSTRAINT ck_question_tag_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_question_tag_question FOREIGN KEY (question_id) REFERENCES question (id),
    CONSTRAINT fk_question_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id)
);

CREATE INDEX idx_question_tag_question_id ON question_tag (question_id);
CREATE INDEX idx_question_tag_tag_id ON question_tag (tag_id);
