-- ---------------------------------------------------------------------------
-- V24 - Reporting permission seeds (P13-04, P13-19)
--
-- Reporting owns no tables: report history is served from the general-purpose background_job table
-- (job_type = 'REPORT_GENERATION'), and the report catalog is application code
-- (specs/22-P13-Reporting-V1.5.md §25, §29, §30, §8). No migration creates a reporting table.
--
-- This migration therefore only seeds the report permission catalogue (§75). The identifiers use the
-- platform's UPPER_SNAKE convention; they are implementation candidates and are recorded in
-- docs/known-gaps.md.
--
-- Every statement is idempotent.
-- ---------------------------------------------------------------------------

INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'REPORT_STUDENT_READ',     'Read student reports',     'Generate student-scope reports',    'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'REPORT_ASSESSMENT_READ',  'Read assessment reports',  'Generate assessment reports',       'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'REPORT_CONTENT_READ',     'Read content reports',     'Generate content reports',          'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'REPORT_COMMERCE_READ',    'Read commerce reports',    'Generate commerce reports',         'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'REPORT_OPERATIONS_READ',  'Read operations reports',  'Generate operations reports',       'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'REPORT_EXECUTIVE_READ',   'Read executive reports',   'Generate executive reports',        'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'REPORT_HISTORY_READ',     'Read report history',      'List report generation history',    'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'REPORT_GENERATE',         'Generate reports',         'Request report generation',         'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- ADMIN: the full reporting capability set, including cross-user report visibility.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('REPORT_STUDENT_READ', 'REPORT_ASSESSMENT_READ', 'REPORT_CONTENT_READ',
        'REPORT_COMMERCE_READ', 'REPORT_OPERATIONS_READ', 'REPORT_EXECUTIVE_READ', 'REPORT_HISTORY_READ',
        'REPORT_GENERATE')
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- STAFF: assessment, content and student reporting; no executive or financial scope.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('REPORT_STUDENT_READ', 'REPORT_ASSESSMENT_READ', 'REPORT_CONTENT_READ',
        'REPORT_HISTORY_READ', 'REPORT_GENERATE')
WHERE r.code = 'STAFF'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- FINANCE: commerce reporting only.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('REPORT_COMMERCE_READ', 'REPORT_HISTORY_READ', 'REPORT_GENERATE')
WHERE r.code = 'FINANCE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- STUDENT deliberately receives no report permission: P1 froze the invariant that a student's
-- permission set is empty (students are authorized by role and self-scope, not by permission codes).
-- Student self-service reporting therefore needs an explicit authorization decision when the
-- student reports are implemented; it is recorded in docs/known-gaps.md rather than resolved here.
