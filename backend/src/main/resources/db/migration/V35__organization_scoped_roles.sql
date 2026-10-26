-- ---------------------------------------------------------------------------
-- V35 - organization-owned roles (D-05, R2, Gate B answer B1 = shape S2)
--
-- S2: role.organization_id NULL means the role is PLATFORM-WIDE (shared by every organization);
-- a non-null value means the role belongs to that organization. The seven seeded roles stay NULL.
--
-- ACCEPTED CONSEQUENCE, recorded in the Gate B memo and ADR-0045: because the seeded roles remain
-- shared, an ADMIN editing ADMIN / STAFF / STUDENT / REVIEWER / SUPPORT / FINANCE still affects every
-- organization. R2 is therefore PARTIALLY met by this change, not fully met. Only roles an
-- organization created itself are isolated. Moving to full isolation later means copying the seeded
-- roles per organization at creation time and needs no change to any guard.
--
-- No table is invented and no frozen column is removed: this is an additive column plus a
-- constraint swap. The global unique on role.code is replaced by two partial uniques so that
-- (a) a platform-wide code stays unique installation-wide, and
-- (b) a code stays unique within one organization, while
-- (c) two organizations may each own a role with the same code, which is the point of the column.
-- ---------------------------------------------------------------------------

ALTER TABLE role ADD COLUMN organization_id UUID;

-- Existing rows are all platform-wide. The seven seeded roles are referenced by code from
-- IdentityConstants and must stay resolvable installation-wide, so they stay NULL by construction.
UPDATE role SET organization_id = NULL WHERE organization_id IS NULL;

ALTER TABLE role
    ADD CONSTRAINT fk_role_organization FOREIGN KEY (organization_id) REFERENCES organization (id);

-- Replace the global unique with two partial uniques (PostgreSQL treats NULLs as distinct, so a
-- plain UNIQUE (organization_id, code) would not constrain the platform-wide rows at all).
ALTER TABLE role DROP CONSTRAINT uk_role_code;

CREATE UNIQUE INDEX uk_role_code_platform ON role (code) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX uk_role_code_organization ON role (organization_id, code)
    WHERE organization_id IS NOT NULL;

CREATE INDEX ix_role_organization_id ON role (organization_id);