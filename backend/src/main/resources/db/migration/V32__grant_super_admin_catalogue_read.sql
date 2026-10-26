-- ---------------------------------------------------------------------------
-- V32 - Grant ROLE_VIEW and PERMISSION_VIEW to SUPER_ADMIN (D-01, ADR-0032 amendment)
--
-- The platform seeds screens (`docs/frontend/super-admin-flow.md` §12-§13) render the role
-- catalogue and the permission catalogue read-only. Both endpoints are guarded by
-- specs/10-P1-Identity-V1.5.md §68:
--
--   roles        LIST -> ROLE_VIEW
--   permissions  GET  -> PERMISSION_VIEW
--
-- V29 attached exactly the eight PLATFORM_* codes to SUPER_ADMIN (ADR-0032: "the platform
-- permission catalogue (PLATFORM_*) is granted to SUPER_ADMIN only"). Read as an exhaustive
-- grant, that left SUPER_ADMIN without the two *read* permissions its own screens require, so
-- /platform/seeds/roles and /platform/seeds/permissions 403 on load (D-01).
--
-- ADR-0032 is amended to read "PLATFORM_* plus the base role/permission read permissions
-- required by the seeds screens". This migration is the code half of that amendment.
--
-- Granted:   ROLE_VIEW, PERMISSION_VIEW  (read-only catalogue visibility)
-- NOT granted, and deliberately so:
--   ROLE_CREATE, ROLE_UPDATE, ROLE_PERMISSION_ASSIGN  - the seeds screens are read-only, and
--     ADR-0032 keeps SUPER_ADMIN's authoring surface out of scope until Gate A decides it.
--   USER_VIEW, AUDIT_LOG_VIEW, ANALYTICS_VIEW, SYSTEM_HEALTH_VIEW - SUPER_ADMIN's surface is
--     /platform/*, not /admin/*. SUPER_ADMINPermissionCatalogueTest pins the exact set.
--
-- Forward migration: V29 is already applied everywhere; migrations are append-only. Idempotent
-- via ON CONFLICT on the role_permission natural key.
-- ---------------------------------------------------------------------------

INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('ROLE_VIEW', 'PERMISSION_VIEW')
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
