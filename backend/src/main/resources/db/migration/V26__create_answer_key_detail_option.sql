-- ---------------------------------------------------------------------------
-- V26 - answer_key_detail_option child table (ADR-0025, GAP-072)
--
-- Adds a child table so a MULTIPLE_CHOICE question with more than one correct
-- option can be represented. answer_key_detail.correct_option_id is retained
-- for single-correct and free-text keys; when child rows exist they form the
-- authoritative correct option set.
-- ---------------------------------------------------------------------------
CREATE TABLE answer_key_detail_option (
    id                    UUID NOT NULL,
    answer_key_detail_id  UUID NOT NULL,
    question_option_id    UUID NOT NULL,
    CONSTRAINT pk_answer_key_detail_option PRIMARY KEY (id),
    CONSTRAINT uk_answer_key_detail_option_pair UNIQUE (answer_key_detail_id, question_option_id),
    CONSTRAINT fk_answer_key_detail_option_detail
        FOREIGN KEY (answer_key_detail_id) REFERENCES answer_key_detail (id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_key_detail_option_option
        FOREIGN KEY (question_option_id) REFERENCES question_option (id)
);

CREATE INDEX idx_answer_key_detail_option_detail_id ON answer_key_detail_option (answer_key_detail_id);
CREATE INDEX idx_answer_key_detail_option_option_id ON answer_key_detail_option (question_option_id);