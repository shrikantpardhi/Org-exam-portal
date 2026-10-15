-- ---------------------------------------------------------------------------
-- V15 - Evaluation permission catalog (P6-04/P6-22)
--
-- specs/15-P6-Evaluation-V1.5.md §100 names the P6 business error codes and §50/§88-§91 name the
-- frozen endpoints, but the frozen Identity catalogue defines no Evaluation permission codes.
-- The minimal operational codes required by the P6 authorization matrix are seeded here and
-- recorded in docs/known-gaps.md (GAP-070), following the same pattern as GAP-056 (Delivery).
--
-- Every statement is idempotent.
-- ---------------------------------------------------------------------------

INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'ANSWER_KEY_VIEW',          'View answer keys',        'View and list answer keys and their details',        'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ANSWER_KEY_MANAGE',        'Manage answer keys',      'Create and edit answer keys and their details',      'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ANSWER_KEY_PUBLISH',       'Publish answer keys',     'Publish an answer key version',                      'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'EVALUATION_VIEW',          'View evaluations',        'Read evaluation internals',                          'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'EVALUATION_VIEW_HISTORY',  'View evaluation history', 'Read the evaluation version history of an attempt',  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'EVALUATION_RUN',           'Run evaluation',          'Evaluate and re-evaluate a submitted attempt',       'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'RESULT_VIEW',              'View results',            'Read any result and the rankings',                   'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'RESULT_PUBLISH',           'Publish results',         'Publish a calculated result',                        'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'SCORECARD_VIEW',           'View scorecards',         'Read any scorecard reference',                       'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SCORECARD_GENERATE',       'Generate scorecards',     'Generate a scorecard artifact',                      'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- ADMIN: the full Evaluation capability set.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN (
        'ANSWER_KEY_VIEW', 'ANSWER_KEY_MANAGE', 'ANSWER_KEY_PUBLISH',
        'EVALUATION_VIEW', 'EVALUATION_VIEW_HISTORY', 'EVALUATION_RUN',
        'RESULT_VIEW', 'RESULT_PUBLISH',
        'SCORECARD_VIEW', 'SCORECARD_GENERATE'
    )
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- STAFF: operational evaluation and result publication (no answer-key authoring by default).
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN (
        'ANSWER_KEY_VIEW',
        'EVALUATION_VIEW', 'EVALUATION_VIEW_HISTORY', 'EVALUATION_RUN',
        'RESULT_VIEW', 'RESULT_PUBLISH',
        'SCORECARD_VIEW', 'SCORECARD_GENERATE'
    )
WHERE r.code = 'STAFF'
ON CONFLICT (role_id, permission_id) DO NOTHING;
