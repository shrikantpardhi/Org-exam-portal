-- ---------------------------------------------------------------------------
-- V17 - Analytics permission catalog (P8-11)
--
-- specs/17-P8-Analytics-V1.5.md §57 freezes the read endpoints and §58 states recalculation is a
-- controlled operational function, not public CRUD. The frozen Identity catalogue defines no
-- Analytics codes, so the minimal staff read/operate codes are seeded here and recorded in
-- docs/known-gaps.md (GAP-089), following the GAP-056/GAP-070 precedent.
--
-- Student analytics is self-scoped by ownership (§107) and needs no permission code.
-- Every statement is idempotent.
-- ---------------------------------------------------------------------------

INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'ANALYTICS_VIEW',    'View analytics',    'Read assessment, question, topic and organization analytics', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ANALYTICS_REFRESH', 'Refresh analytics', 'Run a bounded incremental analytics refresh',                 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ANALYTICS_REBUILD', 'Rebuild analytics', 'Run a full analytics rebuild',                                'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- ADMIN: the full Analytics capability set.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('ANALYTICS_VIEW', 'ANALYTICS_REFRESH', 'ANALYTICS_REBUILD')
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- STAFF: read-only analytics.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('ANALYTICS_VIEW')
WHERE r.code = 'STAFF'
ON CONFLICT (role_id, permission_id) DO NOTHING;
