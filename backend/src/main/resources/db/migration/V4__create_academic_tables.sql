-- ---------------------------------------------------------------------------
-- V4 - Academic schema (P2-02)
--
-- Creates the seven frozen Academic tables from
-- specs/11-P2-Academic-V1.5.md section 9 and specs/04-Database-Schema-V1.5.md section 26.
--
--   exam, subject, chapter, topic, sub_topic, language, tag
--
-- Design notes:
--   * Columns, types, keys, unique constraints, and indexes are taken verbatim from
--     the frozen schema. No table or column is invented.
--   * Business codes are unique: exam.code globally, and (parent_id, code) for each
--     hierarchy level. The PostgreSQL unique constraint is the final authority.
--   * Status columns use controlled VARCHAR values with CHECK constraints, the
--     schema's approved enum strategy (04-Database-Schema-V1.5.md section 10).
--   * The hierarchy is relational: each child references its immediate parent only.
--   * Migration numbering: P1 already used V2 (identity tables) and V3 (identity seeds),
--     so Academic is V4/V5 rather than the schema document's recommended V3/V13.
--     The schema document states the grouping is "Recommended" and "actual migration
--     numbering may differ". Recorded in docs/known-gaps.md (GAP-016).
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- exam
-- ---------------------------------------------------------------------------
CREATE TABLE exam (
    id          UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    CONSTRAINT pk_exam PRIMARY KEY (id),
    CONSTRAINT uk_exam_code UNIQUE (code),
    CONSTRAINT ck_exam_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

-- ---------------------------------------------------------------------------
-- subject
-- ---------------------------------------------------------------------------
CREATE TABLE subject (
    id          UUID         NOT NULL,
    exam_id     UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    CONSTRAINT pk_subject PRIMARY KEY (id),
    CONSTRAINT uk_subject_exam_id_code UNIQUE (exam_id, code),
    CONSTRAINT ck_subject_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_subject_exam FOREIGN KEY (exam_id) REFERENCES exam (id)
);

CREATE INDEX idx_subject_exam_id ON subject (exam_id);

-- ---------------------------------------------------------------------------
-- chapter
-- ---------------------------------------------------------------------------
CREATE TABLE chapter (
    id          UUID         NOT NULL,
    subject_id  UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    CONSTRAINT pk_chapter PRIMARY KEY (id),
    CONSTRAINT uk_chapter_subject_id_code UNIQUE (subject_id, code),
    CONSTRAINT ck_chapter_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_chapter_subject FOREIGN KEY (subject_id) REFERENCES subject (id)
);

CREATE INDEX idx_chapter_subject_id ON chapter (subject_id);

-- ---------------------------------------------------------------------------
-- topic
-- ---------------------------------------------------------------------------
CREATE TABLE topic (
    id          UUID         NOT NULL,
    chapter_id  UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    CONSTRAINT pk_topic PRIMARY KEY (id),
    CONSTRAINT uk_topic_chapter_id_code UNIQUE (chapter_id, code),
    CONSTRAINT ck_topic_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_topic_chapter FOREIGN KEY (chapter_id) REFERENCES chapter (id)
);

CREATE INDEX idx_topic_chapter_id ON topic (chapter_id);

-- ---------------------------------------------------------------------------
-- sub_topic
-- ---------------------------------------------------------------------------
CREATE TABLE sub_topic (
    id          UUID         NOT NULL,
    topic_id    UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    CONSTRAINT pk_sub_topic PRIMARY KEY (id),
    CONSTRAINT uk_sub_topic_topic_id_code UNIQUE (topic_id, code),
    CONSTRAINT ck_sub_topic_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_sub_topic_topic FOREIGN KEY (topic_id) REFERENCES topic (id)
);

CREATE INDEX idx_sub_topic_topic_id ON sub_topic (topic_id);

-- ---------------------------------------------------------------------------
-- language
-- ---------------------------------------------------------------------------
CREATE TABLE language (
    id         UUID         NOT NULL,
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_by UUID,
    created_on TIMESTAMPTZ,
    updated_by UUID,
    updated_on TIMESTAMPTZ,
    CONSTRAINT pk_language PRIMARY KEY (id),
    CONSTRAINT uk_language_code UNIQUE (code),
    CONSTRAINT ck_language_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

-- ---------------------------------------------------------------------------
-- tag
-- ---------------------------------------------------------------------------
CREATE TABLE tag (
    id          UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    CONSTRAINT pk_tag PRIMARY KEY (id),
    CONSTRAINT uk_tag_code UNIQUE (code),
    CONSTRAINT ck_tag_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);
