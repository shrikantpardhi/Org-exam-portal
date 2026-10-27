-- ---------------------------------------------------------------------------
-- V43 - Primacy-transfer permission (ADR-0054)
--
-- ADR-0052 made an organization's primary administrator the only account that may change another
-- user's roles, and designated the primary implicitly when the first administrator was created.
-- ADR-0053 then let the platform owner manage administrators but provided no way to change WHO the
-- primary is; the designation was effectively permanent (recorded as GAP-197).
--
-- Product direction (2026-10-27): the platform owner changes the primary. This migration seeds the
-- permission that act requires.
--
-- Why a separate permission rather than reusing PLATFORM_ADMIN_ROLE_UPDATE:
--   * Editing an administrator's roles changes what one account may do. Transferring primacy changes
--     who may edit everyone else's roles. The second is strictly stronger and should be revocable
--     independently.
--   * The same reasoning V41 used to split PLATFORM_ADMIN_VIEW from PLATFORM_ADMIN_CREATE: a future
--     read-mostly platform role should be able to hold one capability without the other.
--
-- Granted to SUPER_ADMIN only: primacy decides authority inside an organization, which is exactly
-- the decision that must not be reachable from below.
-- ---------------------------------------------------------------------------

INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'PLATFORM_ADMIN_PRIMARY_ASSIGN',
     'Transfer organization primary administrator',
     'Designate which administrator of an organization may change other users'' roles',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code = 'PLATFORM_ADMIN_PRIMARY_ASSIGN'
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO UPDATE SET status = 'ACTIVE', updated_on = now();
