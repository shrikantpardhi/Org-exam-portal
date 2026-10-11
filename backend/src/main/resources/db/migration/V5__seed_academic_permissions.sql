-- ---------------------------------------------------------------------------
-- V5 - Academic permission catalog (P2-11)
--
-- Seeds the Academic permissions from specs/11-P2-Academic-V1.5.md section 55
-- ("implementation candidates") and grants them to the ADMIN role.
--
-- The permission names are configured master data, not a hard-coded enum; the final
-- catalog aligns with Identity role/permission configuration. Every statement is
-- idempotent.
-- ---------------------------------------------------------------------------

INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'EXAM_VIEW',    'View exams',       'View and list exams',        'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'EXAM_CREATE',  'Create exams',     'Create exams',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'EXAM_UPDATE',  'Update exams',     'Update exams',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'EXAM_ARCHIVE', 'Archive exams',    'Archive or deactivate exams','ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'SUBJECT_VIEW',    'View subjects',    'View and list subjects',        'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUBJECT_CREATE',  'Create subjects',  'Create subjects',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUBJECT_UPDATE',  'Update subjects',  'Update subjects',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUBJECT_ARCHIVE', 'Archive subjects', 'Archive or deactivate subjects','ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'CHAPTER_VIEW',    'View chapters',    'View and list chapters',        'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'CHAPTER_CREATE',  'Create chapters',  'Create chapters',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'CHAPTER_UPDATE',  'Update chapters',  'Update chapters',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'CHAPTER_ARCHIVE', 'Archive chapters', 'Archive or deactivate chapters','ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'TOPIC_VIEW',    'View topics',    'View and list topics',        'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'TOPIC_CREATE',  'Create topics',  'Create topics',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'TOPIC_UPDATE',  'Update topics',  'Update topics',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'TOPIC_ARCHIVE', 'Archive topics', 'Archive or deactivate topics','ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'SUB_TOPIC_VIEW',    'View sub-topics',    'View and list sub-topics',        'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUB_TOPIC_CREATE',  'Create sub-topics',  'Create sub-topics',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUB_TOPIC_UPDATE',  'Update sub-topics',  'Update sub-topics',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUB_TOPIC_ARCHIVE', 'Archive sub-topics', 'Archive or deactivate sub-topics','ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'LANGUAGE_VIEW',    'View languages',    'View and list languages',        'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'LANGUAGE_CREATE',  'Create languages',  'Create languages',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'LANGUAGE_UPDATE',  'Update languages',  'Update languages',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'LANGUAGE_ARCHIVE', 'Archive languages', 'Archive or deactivate languages','ACTIVE', '00000000-0000-0000-0000-000000000001', now()),

    (gen_random_uuid(), 'TAG_VIEW',    'View tags',    'View and list tags',        'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'TAG_CREATE',  'Create tags',  'Create tags',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'TAG_UPDATE',  'Update tags',  'Update tags',               'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'TAG_ARCHIVE', 'Archive tags', 'Archive or deactivate tags','ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(),
       r.id,
       p.id,
       'ACTIVE',
       '00000000-0000-0000-0000-000000000001',
       now()
FROM role r
JOIN permission p ON p.code IN (
        'EXAM_VIEW', 'EXAM_CREATE', 'EXAM_UPDATE', 'EXAM_ARCHIVE',
        'SUBJECT_VIEW', 'SUBJECT_CREATE', 'SUBJECT_UPDATE', 'SUBJECT_ARCHIVE',
        'CHAPTER_VIEW', 'CHAPTER_CREATE', 'CHAPTER_UPDATE', 'CHAPTER_ARCHIVE',
        'TOPIC_VIEW', 'TOPIC_CREATE', 'TOPIC_UPDATE', 'TOPIC_ARCHIVE',
        'SUB_TOPIC_VIEW', 'SUB_TOPIC_CREATE', 'SUB_TOPIC_UPDATE', 'SUB_TOPIC_ARCHIVE',
        'LANGUAGE_VIEW', 'LANGUAGE_CREATE', 'LANGUAGE_UPDATE', 'LANGUAGE_ARCHIVE',
        'TAG_VIEW', 'TAG_CREATE', 'TAG_UPDATE', 'TAG_ARCHIVE'
    )
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
