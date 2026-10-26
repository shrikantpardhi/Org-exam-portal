-- ---------------------------------------------------------------------------
-- V29 - SUPER_ADMIN role, PLATFORM organization, platform permissions,
--       and the MAX_ADMINS_PER_ORG business rule (ADR-0032, ADR-0033)
--
-- The frozen role catalogue (specs/10-P1 §9, §64) seeds ADMIN, REVIEWER, STAFF,
-- STUDENT, SUPPORT and FINANCE. A SUPER_ADMIN installation owner is deliberately
-- absent, so it is added here as a role concept with a platform permission
-- catalogue (ADR-0032).
--
-- The frozen single-org model (specs/10-P1 §7) has no organization a SUPER_ADMIN
-- can belong to before any business organization exists. A PLATFORM system
-- organization is introduced as that holder and is filtered from every
-- business-organization list (ADR-0033).
--
-- No SUPER_ADMIN user is created here. The installation owner is created by the
-- environment-gated bootstrap CLI (scripts/bootstrap/bootstrap-super-admin.sh),
-- exactly as the synthetic load cohort is provisioned off the HTTP surface
-- (GAP-085). This keeps a secret-bearing, non-demotable account out of the
-- append-only migration chain and out of every non-bootstrap deployment.
--
-- Idempotent: every INSERT is guarded by ON CONFLICT on the natural unique key.
-- ---------------------------------------------------------------------------

-- 1. SUPER_ADMIN role.
INSERT INTO role (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'SUPER_ADMIN', 'Super Administrator',
     'Installation owner: creates the first business organization and its administrator',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- 2. PLATFORM system organization (the SUPER_ADMIN holder, not a business org).
INSERT INTO organization (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'PLATFORM', 'Platform',
     'System organization that holds the SUPER_ADMIN installation owner',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- 3. Platform permission catalogue (SUPER_ADMIN only).
INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'PLATFORM_ORGANIZATION_VIEW',   'View platform organizations',   'List and view business organizations from the platform scope', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PLATFORM_ORGANIZATION_CREATE', 'Create platform organizations', 'Create a business organization',                              'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PLATFORM_ORGANIZATION_UPDATE', 'Update platform organizations', 'Update a business organization',                              'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PLATFORM_ADMIN_CREATE',        'Create organization admin',     'Create the single ADMIN for a business organization',        'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PLATFORM_SYSTEM_VIEW',         'View platform system info',     'Read application, migration and dependency health',          'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PLATFORM_PROVIDER_VIEW',       'View provider configuration',   'Read email and payment provider configuration',              'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PLATFORM_PROVIDER_UPDATE',     'Update provider configuration', 'Save email and payment provider configuration',              'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PLATFORM_STATUS_VIEW',         'View platform status',          'Read platform-level counts and seed status',                 'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- 4. Attach the platform catalogue to SUPER_ADMIN.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN (
    'PLATFORM_ORGANIZATION_VIEW', 'PLATFORM_ORGANIZATION_CREATE', 'PLATFORM_ORGANIZATION_UPDATE',
    'PLATFORM_ADMIN_CREATE', 'PLATFORM_SYSTEM_VIEW', 'PLATFORM_PROVIDER_VIEW',
    'PLATFORM_PROVIDER_UPDATE', 'PLATFORM_STATUS_VIEW')
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 5. Business rule: exactly one ADMIN per organization (ADR-0032, C3).
INSERT INTO business_rule (id, code, configuration, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'MAX_ADMINS_PER_ORG', '{"maxAdmins": 1}', 'ACTIVE',
     '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;
