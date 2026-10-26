-- ---------------------------------------------------------------------------
-- V33 - Reserved permission set (D-03, ADR-0039)
--
-- specs/20-P11-Administration-V1.5.md §17 requires that an organization administrator cannot widen
-- a role into platform scope, and ADR-0032 states the platform catalogue "is granted to SUPER_ADMIN
-- only". Neither statement was enforced: PUT /roles/{id}/permissions accepted any existing
-- permission id from any caller holding ROLE_PERMISSION_ASSIGN, so an ADMIN could place
-- PLATFORM_* on any role (D-03, reproduced live: 200).
--
-- The reserved set is declared as DATA, not as a Java list (ADR-0023 decision 6, "rules are data,
-- never code"). A future platform permission matching a reserved prefix is covered automatically,
-- without a code change and without a list to keep in sync.
--
--   prefixes          - code prefixes that are reserved by construction
--   codes             - individual reserved codes that are not prefix-derived
--   bypassPermission  - the permission a caller must hold to place a reserved code
--
-- UNBOUNDED_PERMISSION_ASSIGN is seeded GRANTED TO NOBODY. This follows the ADMIN_ROLE_ASSIGN
-- precedent (10-P1 §65, "reserved-but-not-granted"): the capability is defined, named, testable and
-- revocable, but is not attached to any role by the seed. A legitimate change to a reserved grant
-- is a migration — reviewable, ordered, and replayed on every environment — which is the same
-- channel V32 used to change the SUPER_ADMIN set.
--
-- Fail-closed (G-10): RoleService treats a missing, non-ACTIVE or malformed rule as "every
-- permission is reserved, no caller holds the bypass", so the endpoint refuses everything rather
-- than allowing everything. SuperAdminRoleAndReservedPermissionIntegrationTest pins that behaviour.
--
-- Idempotent: ON CONFLICT on both natural unique keys.
-- ---------------------------------------------------------------------------

-- 1. The bypass permission. Deliberately not granted to any role.
INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'UNBOUNDED_PERMISSION_ASSIGN', 'Assign reserved permissions',
     'Bypass the reserved-permission set when replacing a role permission assignment',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- 2. The reserved set, as data.
INSERT INTO business_rule (id, code, configuration, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'RESERVED_PERMISSIONS',
     '{"prefixes": ["PLATFORM_"], "codes": ["UNBOUNDED_PERMISSION_ASSIGN"], "bypassPermission": "UNBOUNDED_PERMISSION_ASSIGN"}',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;
