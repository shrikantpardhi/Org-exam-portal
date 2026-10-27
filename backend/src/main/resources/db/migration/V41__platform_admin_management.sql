-- ---------------------------------------------------------------------------
-- V41 - Platform administrator management permissions (ADR-0053)
--
-- specs/05 §72.5 defines only POST /platform/organizations/{id}/admins: the platform owner can
-- CREATE an organization administrator and nothing else. There is no way to list an organization's
-- administrators, correct their roles, or remove one, which makes the ownership the create endpoint
-- establishes effectively permanent. ADR-0053 records the three additions; this migration seeds the
-- permissions they require.
--
-- Granted to SUPER_ADMIN only: managing another organization's administrators is a platform-tier
-- act, and the whole point of the guard is that an organization cannot reach it from below.
-- ADMIN, SUPPORT and every other role get none of these.
--
-- PLATFORM_ADMIN_VIEW is separate from PLATFORM_ADMIN_CREATE because reading the list is a weaker
-- operation than creating an account, and a future read-only platform role should be able to have
-- one without the other.
-- ---------------------------------------------------------------------------

INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'PLATFORM_ADMIN_ROLE_UPDATE',
     'Update organization administrator roles',
     'Change the non-administrator role assignments of an organization administrator',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PLATFORM_ADMIN_DEACTIVATE',
     'Deactivate organization administrator',
     'Deactivate an organization administrator account',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('PLATFORM_ADMIN_VIEW', 'PLATFORM_ADMIN_ROLE_UPDATE', 'PLATFORM_ADMIN_DEACTIVATE')
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO UPDATE SET status = 'ACTIVE', updated_on = now();

COMMENT ON COLUMN role_permission.status IS
    'ACTIVE or INACTIVE. A withdrawn grant is deactivated, never deleted, so the history of the grant survives (see V40).';
