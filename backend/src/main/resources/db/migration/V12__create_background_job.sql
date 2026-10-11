-- ---------------------------------------------------------------------------
-- V12 - background_job (Infrastructure, P5-23 prerequisite)
--
-- The frozen Infrastructure table used by asynchronous workloads. P5 uses it for the
-- evaluation handoff (specs/14-P5-Delivery-V1.5.md §67) and snapshot preparation job records.
-- It is created here with its exact frozen columns (specs/04-Database-Schema-V1.5.md §35.1)
-- because the Infrastructure phase is implemented later. Recorded in docs/known-gaps.md (GAP-055).
-- ---------------------------------------------------------------------------

CREATE TABLE background_job (
    id              UUID         NOT NULL,
    job_type        VARCHAR(50)  NOT NULL,
    reference_type  VARCHAR(50),
    reference_id    UUID,
    initiated_by    UUID,
    status          VARCHAR(20)  NOT NULL,
    parameters      JSONB,
    result_reference TEXT,
    progress_current INTEGER,
    progress_total  INTEGER,
    error_code      VARCHAR(100),
    error_message   TEXT,
    queued_on       TIMESTAMPTZ,
    started_on      TIMESTAMPTZ,
    completed_on    TIMESTAMPTZ,
    attempt_count   INTEGER,
    last_attempt_on TIMESTAMPTZ,
    CONSTRAINT pk_background_job PRIMARY KEY (id),
    CONSTRAINT ck_background_job_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED', 'CANCELLED')),
    CONSTRAINT fk_background_job_initiated_by FOREIGN KEY (initiated_by) REFERENCES "user" (id)
);

CREATE INDEX idx_background_job_job_type ON background_job (job_type);
CREATE INDEX idx_background_job_initiated_by ON background_job (initiated_by);
CREATE INDEX idx_background_job_status ON background_job (status);
CREATE INDEX idx_background_job_queued_on ON background_job (queued_on);
CREATE INDEX idx_background_job_reference ON background_job (reference_type, reference_id);
