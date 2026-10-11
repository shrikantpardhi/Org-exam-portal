-- ---------------------------------------------------------------------------
-- V10 - Assessment permission catalog and default delivery policy (P4-22)
--
-- Seeds the Blueprint/Assessment permissions from specs/13-P4-Assessment-V1.5.md section 145
-- ("candidates") and one default delivery policy so an assessment version can reference it.
-- Every statement is idempotent.
-- ---------------------------------------------------------------------------

INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'BLUEPRINT_VIEW',    'View blueprints',    'View blueprints',    'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'BLUEPRINT_CREATE',  'Create blueprints',  'Create blueprints',  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'BLUEPRINT_UPDATE',  'Update blueprints',  'Update blueprints',  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'BLUEPRINT_PUBLISH', 'Publish blueprints', 'Publish blueprint versions', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'BLUEPRINT_ARCHIVE', 'Archive blueprints', 'Archive blueprints', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'ASSESSMENT_VIEW',   'View assessments',   'View assessments',   'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ASSESSMENT_CREATE', 'Create assessments', 'Create assessments', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ASSESSMENT_UPDATE', 'Update assessments', 'Update assessments', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'ASSESSMENT_VERSION_VIEW',    'View assessment versions',    'View assessment versions',    'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ASSESSMENT_VERSION_CREATE',  'Create assessment versions',  'Create assessment versions',  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ASSESSMENT_VERSION_UPDATE',  'Update assessment versions',  'Update assessment versions',  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ASSESSMENT_VERSION_VALIDATE','Validate assessment versions','Validate assessment versions','ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ASSESSMENT_VERSION_PUBLISH', 'Publish assessment versions', 'Publish assessment versions', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'ASSESSMENT_QUESTION_MANAGE',       'Manage assessment questions', 'Manage assessment questions',       'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ASSESSMENT_SELECTION_RULE_MANAGE', 'Manage selection rules',      'Manage assessment selection rules', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'ASSESSMENT_SCHEDULE_VIEW',   'View schedules',   'View assessment schedules',   'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ASSESSMENT_SCHEDULE_CREATE', 'Create schedules', 'Create assessment schedules', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ASSESSMENT_SCHEDULE_UPDATE', 'Update schedules', 'Update assessment schedules', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ASSESSMENT_SCHEDULE_CLOSE',  'Close schedules',  'Close assessment schedules',  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'ASSESSMENT_COLLECTION_VIEW',   'View collections',   'View assessment collections',   'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ASSESSMENT_COLLECTION_MANAGE', 'Manage collections', 'Manage assessment collections', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- ADMIN: the full Blueprint/Assessment capability set.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r JOIN permission p ON p.code LIKE 'BLUEPRINT\_%' OR p.code LIKE 'ASSESSMENT\_%'
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- STAFF: authoring without publication.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r JOIN permission p ON p.code IN (
        'BLUEPRINT_VIEW', 'BLUEPRINT_CREATE', 'BLUEPRINT_UPDATE', 'BLUEPRINT_ARCHIVE',
        'ASSESSMENT_VIEW', 'ASSESSMENT_CREATE', 'ASSESSMENT_UPDATE',
        'ASSESSMENT_VERSION_VIEW', 'ASSESSMENT_VERSION_CREATE', 'ASSESSMENT_VERSION_UPDATE',
        'ASSESSMENT_VERSION_VALIDATE',
        'ASSESSMENT_QUESTION_MANAGE', 'ASSESSMENT_SELECTION_RULE_MANAGE',
        'ASSESSMENT_SCHEDULE_VIEW', 'ASSESSMENT_SCHEDULE_CREATE', 'ASSESSMENT_SCHEDULE_UPDATE', 'ASSESSMENT_SCHEDULE_CLOSE',
        'ASSESSMENT_COLLECTION_VIEW', 'ASSESSMENT_COLLECTION_MANAGE'
    )
WHERE r.code = 'STAFF'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- REVIEWER: read only.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r JOIN permission p ON p.code IN (
        'BLUEPRINT_VIEW', 'ASSESSMENT_VIEW', 'ASSESSMENT_VERSION_VIEW', 'ASSESSMENT_SCHEDULE_VIEW', 'ASSESSMENT_COLLECTION_VIEW'
    )
WHERE r.code = 'REVIEWER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Default delivery policy (Delivery-owned; see GAP-040).
-- ---------------------------------------------------------------------------
INSERT INTO delivery_policy (id, code, name, configuration, status, created_by, created_on)
VALUES ('33333333-3333-3333-3333-333333333301', 'DEFAULT', 'Default delivery policy',
        '{"allowBackNavigation": true, "allowSectionSwitch": true, "autoSaveIntervalSeconds": 10, "allowQuestionReview": true}'::jsonb,
        'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;
