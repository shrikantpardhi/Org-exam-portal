-- ---------------------------------------------------------------------------
-- V20 - Communication schema (P10-02, P10-07, P10-15)
--
-- Creates the three frozen Communication tables from specs/19-P10-Communication-V1.5.md section 7
-- and specs/04-Database-Schema-V1.5.md section 33:
--
--   notification, email_template, announcement
--
-- Design notes:
--   * Columns, types, keys, unique constraints and indexes are taken verbatim from the frozen
--     schema. No table or column is invented.
--   * `notification` is deliberately minimal: it has no updated_on/created_by/updated_by and no
--     delivery_status/event_type/template_id/read_on/expires_on (specs/19-P10 §7.1). Email delivery
--     status is represented through background_job, logs, metrics and the provider response — no
--     notification_delivery/email_delivery table (§31).
--   * notification_status is only UNREAD or READ (§8).
--   * announcement.created_by is the frozen FK to "user".
--   * Migration numbering: earlier phases used V2-V19, so Communication is V20/V21.
-- ---------------------------------------------------------------------------

CREATE TABLE notification (
    id                  UUID         NOT NULL,
    user_id             UUID         NOT NULL,
    title               VARCHAR(255) NOT NULL,
    message             TEXT,
    notification_status VARCHAR(20)  NOT NULL,
    created_on          TIMESTAMPTZ,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT ck_notification_status CHECK (notification_status IN ('UNREAD', 'READ')),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES "user" (id)
);

CREATE INDEX idx_notification_user_id ON notification (user_id);
CREATE INDEX idx_notification_notification_status ON notification (notification_status);

CREATE TABLE email_template (
    id         UUID         NOT NULL,
    code       VARCHAR(50)  NOT NULL,
    name       VARCHAR(150) NOT NULL,
    subject    VARCHAR(255),
    body       TEXT,
    status     VARCHAR(20)  NOT NULL,
    created_by UUID,
    created_on TIMESTAMPTZ,
    updated_by UUID,
    updated_on TIMESTAMPTZ,
    CONSTRAINT pk_email_template PRIMARY KEY (id),
    CONSTRAINT uk_email_template_code UNIQUE (code),
    CONSTRAINT ck_email_template_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

CREATE TABLE announcement (
    id          UUID         NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    start_date  TIMESTAMPTZ,
    end_date    TIMESTAMPTZ,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    status      VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_announcement PRIMARY KEY (id),
    CONSTRAINT ck_announcement_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_announcement_created_by FOREIGN KEY (created_by) REFERENCES "user" (id)
);

CREATE INDEX idx_announcement_created_by ON announcement (created_by);
CREATE INDEX idx_announcement_start_date ON announcement (start_date);
CREATE INDEX idx_announcement_end_date ON announcement (end_date);
CREATE INDEX idx_announcement_status ON announcement (status);
