-- ---------------------------------------------------------------------------
-- V7 - Content permission catalog and question templates (P3-03, P3-21)
--
-- Seeds the Content permissions from specs/12-P3-Content-V1.5.md section 128
-- ("candidates") and the initial question templates (section 18 / 04-Schema §59/§61).
-- Every statement is idempotent.
-- ---------------------------------------------------------------------------

INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'QUESTION_TEMPLATE_VIEW',   'View question templates',   'View question templates',    'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_TEMPLATE_CREATE', 'Create question templates', 'Create question templates',  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_TEMPLATE_UPDATE', 'Update question templates', 'Update question templates',  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'QUESTION_VIEW',          'View questions',      'View and list questions',             'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_CREATE',        'Create questions',    'Create questions',                    'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_UPDATE',        'Update questions',    'Update draft question metadata',      'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_SUBMIT_REVIEW', 'Submit for review',   'Submit a question version for review','ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_REVIEW',        'Review questions',    'Approve or reject a question version','ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_PUBLISH',       'Publish questions',   'Publish an approved question version','ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_ARCHIVE',       'Archive questions',   'Archive or deactivate questions',     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'QUESTION_OPTION_CREATE',  'Create options',  'Create question options',   'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_OPTION_UPDATE',  'Update options',  'Update question options',   'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_OPTION_ARCHIVE', 'Archive options', 'Archive question options',  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'QUESTION_ASSET_CREATE', 'Upload assets',  'Upload question assets',  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_ASSET_VIEW',   'View assets',    'View question assets',    'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_ASSET_REMOVE', 'Remove assets',  'Remove question assets',  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'QUESTION_EXPLANATION_CREATE', 'Create explanation', 'Create a question explanation', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_EXPLANATION_UPDATE', 'Update explanation', 'Update a question explanation', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'QUESTION_HINT_CREATE', 'Create hint', 'Create a question hint', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_HINT_UPDATE', 'Update hint', 'Update a question hint', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'QUESTION_PREVIOUS_EXAM_VIEW',   'View previous-exam history',   'View previous exam history',   'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'QUESTION_PREVIOUS_EXAM_MANAGE', 'Manage previous-exam history', 'Manage previous exam history', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- ADMIN: the full Content capability set.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r JOIN permission p ON p.code LIKE 'QUESTION\_%'
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- REVIEWER: read + review only (not authoring, not publish). A reviewer is not a staff author.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r JOIN permission p ON p.code IN ('QUESTION_TEMPLATE_VIEW', 'QUESTION_VIEW', 'QUESTION_REVIEW')
WHERE r.code = 'REVIEWER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- STAFF: authoring set (no review, no publish).
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r JOIN permission p ON p.code IN (
        'QUESTION_TEMPLATE_VIEW', 'QUESTION_VIEW', 'QUESTION_CREATE', 'QUESTION_UPDATE',
        'QUESTION_SUBMIT_REVIEW', 'QUESTION_ARCHIVE',
        'QUESTION_OPTION_CREATE', 'QUESTION_OPTION_UPDATE', 'QUESTION_OPTION_ARCHIVE',
        'QUESTION_ASSET_CREATE', 'QUESTION_ASSET_VIEW', 'QUESTION_ASSET_REMOVE',
        'QUESTION_EXPLANATION_CREATE', 'QUESTION_EXPLANATION_UPDATE',
        'QUESTION_HINT_CREATE', 'QUESTION_HINT_UPDATE',
        'QUESTION_PREVIOUS_EXAM_VIEW', 'QUESTION_PREVIOUS_EXAM_MANAGE'
    )
WHERE r.code = 'STAFF'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Question templates (initial pilot catalog; configuration is master data)
-- ---------------------------------------------------------------------------
INSERT INTO question_template (id, code, name, description, configuration, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'SINGLE_CHOICE', 'Single choice',
     'Exactly one correct option',
     '{"requires_latex_parsing": true, "option_based": true, "answer_model": "SINGLE_OPTION", "correct_option_count": 1, "min_options": 2, "max_options": 10}'::jsonb,
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'MULTIPLE_CHOICE', 'Multiple choice',
     'One or more correct options',
     '{"requires_latex_parsing": true, "option_based": true, "answer_model": "MULTIPLE_OPTIONS", "min_correct_options": 1, "min_options": 2, "max_options": 10}'::jsonb,
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'TRUE_FALSE', 'True or false',
     'Two options, exactly one correct',
     '{"requires_latex_parsing": false, "option_based": true, "answer_model": "SINGLE_OPTION", "correct_option_count": 1, "min_options": 2, "max_options": 2}'::jsonb,
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'INTEGER', 'Integer',
     'A single integer answer',
     '{"requires_latex_parsing": true, "option_based": false, "answer_model": "INTEGER"}'::jsonb,
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'DECIMAL', 'Decimal',
     'A single decimal answer',
     '{"requires_latex_parsing": true, "option_based": false, "answer_model": "DECIMAL"}'::jsonb,
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'FILL_IN_THE_BLANK', 'Fill in the blank',
     'A short textual answer',
     '{"requires_latex_parsing": true, "option_based": false, "answer_model": "TEXT"}'::jsonb,
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;
