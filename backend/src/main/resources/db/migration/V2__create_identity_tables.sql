-- ---------------------------------------------------------------------------
-- V2 - Identity schema (P1-01)
--
-- Creates the seven frozen Identity tables from specs/04-Database-Schema-V1.5.md
-- (section 25) and specs/10-P1-Identity-V1.5.md (sections 7-14).
--
--   organization
--   user
--   role
--   permission
--   user_role
--   role_permission
--   login_session
--
-- Design notes:
--   * Table/column names, types, keys, and indexes are taken verbatim from the
--     frozen schema. No table or column is invented.
--   * `user` is a reserved PostgreSQL keyword, so it is referenced as "user"
--     everywhere (migration, JPA mapping, and queries). The physical table name
--     is still the frozen `user`.
--   * Status columns use controlled VARCHAR values with CHECK constraints, which
--     is the schema's approved enum implementation strategy
--     (specs/04-Database-Schema-V1.5.md section 10: "controlled VARCHAR values
--     with check constraints ... Do not allow arbitrary free-text status values.").
--   * login_session carries no audit fields and uses session_status, not status
--     (specs/04-Database-Schema-V1.5.md section 25.7).
--   * Hard cascading deletes are not used for identity history.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- organization
-- ---------------------------------------------------------------------------
CREATE TABLE organization (
    id          UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    CONSTRAINT pk_organization PRIMARY KEY (id),
    CONSTRAINT uk_organization_code UNIQUE (code),
    CONSTRAINT ck_organization_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

-- ---------------------------------------------------------------------------
-- user
-- ---------------------------------------------------------------------------
CREATE TABLE "user" (
    id              UUID         NOT NULL,
    organization_id UUID         NOT NULL,
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    email           VARCHAR(255) NOT NULL,
    mobile          VARCHAR(20),
    password        VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    created_by      UUID,
    created_on      TIMESTAMPTZ,
    updated_by      UUID,
    updated_on      TIMESTAMPTZ,
    CONSTRAINT pk_user PRIMARY KEY (id),
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT uk_user_mobile UNIQUE (mobile),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_user_organization FOREIGN KEY (organization_id) REFERENCES organization (id)
);

CREATE INDEX idx_user_organization_id ON "user" (organization_id);

-- ---------------------------------------------------------------------------
-- role
-- ---------------------------------------------------------------------------
CREATE TABLE role (
    id          UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    CONSTRAINT pk_role PRIMARY KEY (id),
    CONSTRAINT uk_role_code UNIQUE (code),
    CONSTRAINT ck_role_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

-- ---------------------------------------------------------------------------
-- permission
-- ---------------------------------------------------------------------------
CREATE TABLE permission (
    id          UUID         NOT NULL,
    code        VARCHAR(100) NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    created_by  UUID,
    created_on  TIMESTAMPTZ,
    updated_by  UUID,
    updated_on  TIMESTAMPTZ,
    CONSTRAINT pk_permission PRIMARY KEY (id),
    CONSTRAINT uk_permission_code UNIQUE (code),
    CONSTRAINT ck_permission_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

-- ---------------------------------------------------------------------------
-- user_role
-- ---------------------------------------------------------------------------
CREATE TABLE user_role (
    id         UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    role_id    UUID        NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_by UUID,
    created_on TIMESTAMPTZ,
    updated_by UUID,
    updated_on TIMESTAMPTZ,
    CONSTRAINT pk_user_role PRIMARY KEY (id),
    CONSTRAINT uk_user_role_user_id_role_id UNIQUE (user_id, role_id),
    CONSTRAINT ck_user_role_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES "user" (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role (id)
);

CREATE INDEX idx_user_role_user_id ON user_role (user_id);
CREATE INDEX idx_user_role_role_id ON user_role (role_id);

-- ---------------------------------------------------------------------------
-- role_permission
-- ---------------------------------------------------------------------------
CREATE TABLE role_permission (
    id            UUID        NOT NULL,
    role_id       UUID        NOT NULL,
    permission_id UUID        NOT NULL,
    status        VARCHAR(20) NOT NULL,
    created_by    UUID,
    created_on    TIMESTAMPTZ,
    updated_by    UUID,
    updated_on    TIMESTAMPTZ,
    CONSTRAINT pk_role_permission PRIMARY KEY (id),
    CONSTRAINT uk_role_permission_role_id_permission_id UNIQUE (role_id, permission_id),
    CONSTRAINT ck_role_permission_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission (id)
);

CREATE INDEX idx_role_permission_role_id ON role_permission (role_id);
CREATE INDEX idx_role_permission_permission_id ON role_permission (permission_id);

-- ---------------------------------------------------------------------------
-- login_session
-- ---------------------------------------------------------------------------
CREATE TABLE login_session (
    id             UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    login_time     TIMESTAMPTZ  NOT NULL,
    logout_time    TIMESTAMPTZ,
    session_status VARCHAR(20)  NOT NULL,
    ip_address     VARCHAR(100),
    device_info    TEXT,
    CONSTRAINT pk_login_session PRIMARY KEY (id),
    CONSTRAINT ck_login_session_session_status CHECK (session_status IN ('ACTIVE', 'EXPIRED', 'LOGGED_OUT')),
    CONSTRAINT fk_login_session_user FOREIGN KEY (user_id) REFERENCES "user" (id)
);

CREATE INDEX idx_login_session_user_id ON login_session (user_id);
CREATE INDEX idx_login_session_login_time ON login_session (login_time);
CREATE INDEX idx_login_session_session_status ON login_session (session_status);
