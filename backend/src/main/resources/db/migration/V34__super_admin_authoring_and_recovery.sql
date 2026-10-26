-- ---------------------------------------------------------------------------
-- V34 - SUPER_ADMIN role authoring and the admin recovery capability (Gate A)
--
-- Gate A approved reading A: SUPER_ADMIN authors roles (create, then assign permissions) and holds
-- the reserved-set bypass. It also approved a recovery surface for a locked-out ADMIN and a
-- cross-organization user list (ADR-0042).
--
-- Granted to SUPER_ADMIN:
--   ROLE_CREATE, ROLE_UPDATE, ROLE_PERMISSION_ASSIGN   - role authoring (Gate A / A2)
--   UNBOUNDED_PERMISSION_ASSIGN                        - reserved-set bypass (Gate A / A2b)
--   PLATFORM_ADMIN_VIEW                                - new: cross-org user list (Gate A / A4)
--   PLATFORM_ADMIN_RECOVER                             - new: admin recovery (Gate A / A1)
--
-- NOT granted, and deliberately so:
--   ROLE_DELETE / role archiving does not exist in the frozen API (05-API-Contract §24).
--   USER_* - SUPER_ADMIN's user surface is /platform/*, not /admin/*. The new PLATFORM_ADMIN_VIEW
--     is read-only; it grants no mutation of another organization's users.
--
-- ADR-0039 consequence: SUPER_ADMIN now satisfies the reserved-set bypass, so
-- RESERVED_PERMISSION_DENIED is unreachable for it. It remains reachable for ADMIN and every other
-- principal, which is the case D-03 was about. Recorded in ADR-0042.
--
-- Idempotent: ON CONFLICT on both natural unique keys.
-- ---------------------------------------------------------------------------

-- 1. The two new platform permissions.
INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'PLATFORM_ADMIN_VIEW', 'View users across organizations',
     'List and inspect users of every organization, including the platform organization',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PLATFORM_ADMIN_RECOVER', 'Recover a locked-out administrator',
     'Issue a password reset and revoke sessions for an administrator of a business organization',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- 2. Grant the platform capabilities to SUPER_ADMIN.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN (
    'ROLE_CREATE', 'ROLE_UPDATE', 'ROLE_PERMISSION_ASSIGN',
    'UNBOUNDED_PERMISSION_ASSIGN', 'PLATFORM_ADMIN_VIEW', 'PLATFORM_ADMIN_RECOVER')
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO UPDATE SET status = 'ACTIVE';
