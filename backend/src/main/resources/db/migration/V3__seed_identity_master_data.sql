-- ---------------------------------------------------------------------------
-- V3 - Identity master data (P1-01)
--
-- Seeds the identity reference data mandated by the P1 specification:
--   * default organization (structurally required: user.organization_id is NOT
--     NULL and the system actor is a user)
--   * the well-known system actor (specs/10-P1-Identity-V1.5.md section 15)
--   * the initial role concepts (section 64)
--   * the initial permission catalog for P1 Identity (section 65)
--   * explicit ADMIN -> identity management permission assignments (section 66)
--
-- Every statement is idempotent (ON CONFLICT ... DO NOTHING) so the migration is
-- safe to re-run against a database that already holds the reference rows.
--
-- The system actor stores a non-usable placeholder credential. Login is
-- unconditionally rejected for the system actor; the placeholder can never be a
-- valid password for any real user.
--
-- The ADMIN_ROLE_ASSIGN permission is intentionally seeded but NOT granted to any
-- role (specs/10-P1-Identity-V1.5.md section 65: "reserved-but-not-granted").
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- Default organization
-- ---------------------------------------------------------------------------
INSERT INTO organization (id, code, name, description, status, created_by, created_on)
VALUES ('11111111-1111-1111-1111-111111111111',
        'DEFAULT',
        'Default Organization',
        'Single-organization deployment default',
        'ACTIVE',
        '00000000-0000-0000-0000-000000000001',
        now())
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- System actor
-- ---------------------------------------------------------------------------
INSERT INTO "user" (id, organization_id, first_name, last_name, email, mobile, password, status,
                    created_by, created_on)
VALUES ('00000000-0000-0000-0000-000000000001',
        '11111111-1111-1111-1111-111111111111',
        'System',
        'Actor',
        'system@platform.internal',
        NULL,
        '{noop}system-actor-login-is-disabled-and-this-value-is-never-a-valid-password',
        'ACTIVE',
        '00000000-0000-0000-0000-000000000001',
        now())
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Roles
-- ---------------------------------------------------------------------------
INSERT INTO role (id, code, name, description, status, created_by, created_on) VALUES
    ('11111111-1111-1111-1111-111111111101', 'ADMIN',    'Administrator', 'Full platform administration',      'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('11111111-1111-1111-1111-111111111102', 'REVIEWER', 'Reviewer',      'Content review role',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('11111111-1111-1111-1111-111111111103', 'STAFF',    'Staff',         'Operational staff role',            'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('11111111-1111-1111-1111-111111111104', 'STUDENT',  'Student',       'Student self-service role',         'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('11111111-1111-1111-1111-111111111105', 'SUPPORT',  'Support',       'Support operations role',           'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('11111111-1111-1111-1111-111111111106', 'FINANCE',  'Finance',       'Finance and commerce role',         'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Permissions (P1 Identity catalog only)
-- ---------------------------------------------------------------------------
INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    ('22222222-2222-2222-2222-222222222201', 'USER_VIEW',              'View users',                 'View and list platform users',                 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('22222222-2222-2222-2222-222222222202', 'USER_CREATE',            'Create users',               'Create platform users',                        'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('22222222-2222-2222-2222-222222222203', 'USER_UPDATE',            'Update users',               'Update platform user profiles',                'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('22222222-2222-2222-2222-222222222204', 'USER_ARCHIVE',           'Archive users',              'Archive or deactivate platform users',         'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('22222222-2222-2222-2222-222222222205', 'ROLE_VIEW',              'View roles',                 'View and list roles',                          'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('22222222-2222-2222-2222-222222222206', 'ROLE_CREATE',            'Create roles',               'Create roles',                                 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('22222222-2222-2222-2222-222222222207', 'ROLE_UPDATE',            'Update roles',               'Update roles',                                 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('22222222-2222-2222-2222-222222222208', 'ROLE_PERMISSION_VIEW',   'View role permissions',      'View a role''s effective permissions',         'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('22222222-2222-2222-2222-222222222209', 'ROLE_PERMISSION_ASSIGN', 'Assign role permissions',    'Replace a role''s permission assignments',     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('22222222-2222-2222-2222-222222222210', 'PERMISSION_VIEW',        'View permissions',           'View the permission catalog',                  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('22222222-2222-2222-2222-222222222211', 'SESSION_VIEW',           'View login sessions',        'View login session records',                   'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    ('22222222-2222-2222-2222-222222222212', 'ADMIN_ROLE_ASSIGN',      'Assign the ADMIN role',      'Assign or revoke the ADMIN role (reserved)',   'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- ADMIN -> identity management permissions (explicit seed mapping)
-- ADMIN_ROLE_ASSIGN is deliberately not granted here.
-- ---------------------------------------------------------------------------
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(),
       r.id,
       p.id,
       'ACTIVE',
       '00000000-0000-0000-0000-000000000001',
       now()
FROM role r
JOIN permission p ON p.code IN (
        'USER_VIEW', 'USER_CREATE', 'USER_UPDATE', 'USER_ARCHIVE',
        'ROLE_VIEW', 'ROLE_CREATE', 'ROLE_UPDATE',
        'ROLE_PERMISSION_VIEW', 'ROLE_PERMISSION_ASSIGN',
        'PERMISSION_VIEW', 'SESSION_VIEW'
    )
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
