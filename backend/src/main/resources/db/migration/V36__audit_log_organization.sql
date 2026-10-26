-- ---------------------------------------------------------------------------
-- V36 - audit_log carries an organization (D-11, R2, Gate B answer B4)
--
-- The frozen schema (04-Schema §34.4) has no organization column, so the ADMIN audit explorer at
-- organization A returns every row of a two-organization deployment. Organization was derived through
-- user at read time, which works only while the audited entity has a user, and reports nothing for
-- organizations, business rules and provider configuration.
--
-- This adds the column, records it at write time, and backfills it where the audited entity's owner is
-- derivable.
--
-- BACKFILL POLICY (Gate B memo §8): derive where derivable, NULL otherwise.
-- Deliberately NOT backfilling everything to DEFAULT: that would relabel another organization's
-- history as DEFAULT's, which is worse than leaving it unattributed because it would look correct.
--
-- A NULL organization_id means PLATFORM-WIDE (system actor, scheduler, worker activity). Those rows
-- are not shown to any organization's ADMIN, matching how the platform tier owns them.
-- ---------------------------------------------------------------------------

ALTER TABLE audit_log ADD COLUMN organization_id UUID;

ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_log_organization FOREIGN KEY (organization_id) REFERENCES organization (id);

CREATE INDEX ix_audit_log_organization_id ON audit_log (organization_id);

-- ---------------------------------------------------------------------------
-- Backfill, in derivability order. Each step only touches rows still unattributed, so an earlier
-- (more specific) rule wins.
-- ---------------------------------------------------------------------------

-- 1. The row's own actor carries an organization. This covers every human-performed action.
UPDATE audit_log a
SET organization_id = u.organization_id
FROM "user" u
WHERE a.user_id = u.id
  AND a.organization_id IS NULL;

-- 2. The audited entity IS an organization: attribute the row to that organization.
UPDATE audit_log
SET organization_id = entity_id
WHERE organization_id IS NULL
  AND entity_name = 'Organization'
  AND entity_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM organization o WHERE o.id = audit_log.entity_id);

-- 3. The audited entity is a user: attribute the row to that user's organization.
UPDATE audit_log a
SET organization_id = u.organization_id
FROM "user" u
WHERE a.organization_id IS NULL
  AND a.entity_name = 'User'
  AND a.entity_id = u.id;

-- 4. The audited entity is a role: a platform-wide role stays NULL; an organization-owned role is
--    attributed to its owner (V35 added role.organization_id).
UPDATE audit_log a
SET organization_id = r.organization_id
FROM role r
WHERE a.organization_id IS NULL
  AND a.entity_name = 'Role'
  AND a.entity_id = r.id
  AND r.organization_id IS NOT NULL;
