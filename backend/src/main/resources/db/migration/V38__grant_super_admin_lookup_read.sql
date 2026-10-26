-- ---------------------------------------------------------------------------
-- V38 - Grant LOOKUP_VIEW to SUPER_ADMIN (ADR-0032 amendment, same shape as V32)
--
-- /platform/seeds/lookups is a read-only screen in the SUPER_ADMIN surface
-- (docs/frontend/super-admin-flow.md §14). It is gated on PLATFORM_SYSTEM_VIEW in the UI, but the
-- API behind it is:
--
--   GET /api/v1/lookups  ->  LOOKUP_VIEW
--
-- V29 attached only the PLATFORM_* codes to SUPER_ADMIN, so the screen loads and then its request
-- 403s. V32 fixed exactly this class of defect for ROLE_VIEW and PERMISSION_VIEW; this is the same
-- fix for the third seeds screen.
--
-- Granted:   LOOKUP_VIEW            (read-only catalogue visibility)
-- NOT granted, deliberately:
--   LOOKUP_CREATE, LOOKUP_UPDATE, LOOKUP_DELETE
--     The seeds screen is read-only, and lookup authoring belongs to the organization administrator
--     (/admin/lookups). Granting write here would give the platform owner an editing surface the
--     spec does not define for it.
--   SYSTEM_HEALTH_VIEW
--     System health was removed from the organization administrator by product decision
--     (2026-10-26) and is not part of the platform surface either; /platform/system already reports
--     dependency health under PLATFORM_SYSTEM_VIEW.
--
-- Forward migration: migrations are append-only. Idempotent via ON CONFLICT on the role_permission
-- natural key, like V32.
-- ---------------------------------------------------------------------------

INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code = 'LOOKUP_VIEW'
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
