-- ---------------------------------------------------------------------------
-- V39 - Organization primary administrator (ADR-0052)
--
-- An organization may hold more than one ADMIN (the cap itself is the MAX_ADMINS_PER_ORG
-- business rule). Role editing inside an organization is restricted to its designated primary
-- administrator, so the database needs to record WHICH administrator that is.
--
-- Why a column on `organization` and not a flag on `user_role`:
--   * A per-assignment boolean permits several primaries per organization, which silently weakens
--     the guard it is supposed to express.
--   * Primacy must be unique per organization by construction, not by convention. A single
--     nullable FK makes 'at most one' a schema property rather than something every writer must
--     remember.
--
-- Fail-closed semantics (deliberate):
--   * NULL means no primary is designated for this organization. In that state NOBODY in the
--     organization may change another user's roles. The state is recoverable because creating the
--     first administrator designates a primary, and creating an administrator is not itself
--     restricted by this guard.
--   * Existing rows are left NULL rather than backfilled from created_on. Guessing would hand
--     role-edit power to whoever happened to be oldest, which is precisely the escalation this
--     column exists to prevent.
--   * ON DELETE SET NULL: deleting a user must not delete the organization, and must not leave a
--     dangling reference. The organization simply loses its primary and re-enters the fail-closed
--     state, which is the safe direction.
--
-- This amends the frozen table in specs/04-Database-Schema §25.2 and is recorded in ADR-0052,
-- exactly the pattern used by V35/V36 (ADR-0047) and V37 (ADR-0051).
-- ---------------------------------------------------------------------------

ALTER TABLE organization
    ADD COLUMN IF NOT EXISTS primary_admin_user_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_organization_primary_admin'
    ) THEN
        ALTER TABLE organization
            ADD CONSTRAINT fk_organization_primary_admin
            FOREIGN KEY (primary_admin_user_id) REFERENCES "user" (id) ON DELETE SET NULL;
    END IF;
END $$;

COMMENT ON COLUMN organization.primary_admin_user_id IS
    'The administrator allowed to change other users'' roles in this organization. NULL means none is designated, in which case role editing is refused (fail-closed). See ADR-0052.';

-- A partial index: only rows that actually designate a primary are interesting, and the column is
-- mostly NULL, so a full index would carry dead weight.
CREATE INDEX IF NOT EXISTS idx_organization_primary_admin
    ON organization (primary_admin_user_id)
    WHERE primary_admin_user_id IS NOT NULL;
