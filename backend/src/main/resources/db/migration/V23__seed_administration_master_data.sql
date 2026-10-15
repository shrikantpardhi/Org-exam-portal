-- ---------------------------------------------------------------------------
-- V23 - Administration permission seeds (P11-05, P11-18, P11-19, P11-15, P11-16, P11-17, P11-18)
--
-- Seeds the Administration permission catalogue from specs/20-P11-Administration-V1.5.md section 80.
--
-- The frozen Identity catalogue does not define these codes, so the documented concepts are seeded
-- here as implementation candidates and recorded in docs/known-gaps.md (GAP-116). Every statement is
-- idempotent.
--
-- The support permissions are granted to SUPPORT, never to STUDENT. No role receives an
-- unconditional bypass: code always resolves an explicit permission (specs/20-P11 §6).
-- ---------------------------------------------------------------------------

INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'ORGANIZATION_SETTING_VIEW',   'View organization settings',   'Read organization configuration',        'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ORGANIZATION_SETTING_UPDATE', 'Update organization settings', 'Change organization configuration',      'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'BUSINESS_RULE_VIEW',          'View business rules',          'Read business rules',                    'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'BUSINESS_RULE_CREATE',        'Create business rules',        'Create business rules',                  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'BUSINESS_RULE_UPDATE',        'Update business rules',        'Update business rules',                  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'LOOKUP_VIEW',                 'View lookups',                 'Read lookup master data',                'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'LOOKUP_CREATE',               'Create lookups',               'Create lookup values',                   'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'LOOKUP_UPDATE',               'Update lookups',               'Update lookup values',                   'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'LOOKUP_DELETE',               'Delete lookups',               'Archive lookup values',                  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'AUDIT_LOG_VIEW',              'View audit logs',              'Search the audit trail',                 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SYSTEM_HEALTH_VIEW',          'View system health',           'Read the aggregated system health view', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'GLOBAL_SEARCH',               'Use global search',            'Use the global search endpoint',         'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'GLOBAL_SEARCH_USER',          'Search users',                 'Search users in global search',          'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'GLOBAL_SEARCH_QUESTION',      'Search questions',             'Search questions in global search',      'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'GLOBAL_SEARCH_ASSESSMENT',    'Search assessments',           'Search assessments in global search',    'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'GLOBAL_SEARCH_COMMERCE',      'Search commerce',              'Search commerce in global search',       'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'GLOBAL_SEARCH_COMMUNICATION', 'Search communication',         'Search announcements in global search',  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'GLOBAL_SEARCH_AUDIT',         'Search the audit trail',       'Search audit logs in global search',     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'IMPORT_EXECUTE',              'Execute imports',              'Run a registered import target',         'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'BULK_EXECUTE',                'Execute bulk operations',      'Run an allow-listed bulk operation',     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ADMIN_DASHBOARD_VIEW',        'View the admin dashboard',     'Read aggregated admin dashboard cards',  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ADMIN_ROLE_ASSIGN',           'Assign roles',                 'Assign or remove roles for a user',      'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUPPORT_USER_VIEW',           'Support: view user',           'Read user identity, masked by default',  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUPPORT_USER_CONTACT',        'Support: view contact',        'See unmasked email and mobile',          'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUPPORT_SESSION_VIEW',        'Support: view sessions',       'Read login session metadata',            'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUPPORT_ATTEMPT_VIEW',        'Support: view attempts',       'Read attempt metadata',                  'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUPPORT_ACCESS_VIEW',         'Support: view access',         'Read access grants',                     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUPPORT_PURCHASE_VIEW',       'Support: view purchases',      'Read purchases without payment reference','ACTIVE','00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUPPORT_PAYMENT_REF_VIEW',    'Support: view payment ref',    'See the payment reference',              'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUPPORT_NOTIFICATION_SEND',   'Support: send notification',   'Send an ad-hoc notification to one user','ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUPPORT_SESSION_REVOKE',      'Support: revoke session',      'Force-invalidate a session',             'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUPPORT_ATTEMPT_RECOVER',     'Support: recover attempt',     'Trigger session recovery for a user',    'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- ADMIN: the full Administration capability set.
--
-- ADMIN_ROLE_ASSIGN is deliberately NOT granted to ADMIN: P1 froze the invariant that assigning the
-- ADMIN role requires a permission the ADMIN role does not itself hold, so an ordinary administrator
-- cannot escalate. The permission exists for the documented, separately-controlled assignment flow.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('ORGANIZATION_SETTING_VIEW', 'ORGANIZATION_SETTING_UPDATE',
        'BUSINESS_RULE_VIEW', 'BUSINESS_RULE_CREATE', 'BUSINESS_RULE_UPDATE',
        'LOOKUP_VIEW', 'LOOKUP_CREATE', 'LOOKUP_UPDATE', 'LOOKUP_DELETE',
        'AUDIT_LOG_VIEW', 'SYSTEM_HEALTH_VIEW', 'GLOBAL_SEARCH', 'GLOBAL_SEARCH_USER',
        'GLOBAL_SEARCH_QUESTION', 'GLOBAL_SEARCH_ASSESSMENT', 'GLOBAL_SEARCH_COMMERCE',
        'GLOBAL_SEARCH_COMMUNICATION', 'GLOBAL_SEARCH_AUDIT', 'IMPORT_EXECUTE', 'BULK_EXECUTE',
        'ADMIN_DASHBOARD_VIEW')
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- SUPPORT: the frozen support scope only (no settings, no rules, no audit by default).
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('SUPPORT_USER_VIEW', 'SUPPORT_USER_CONTACT', 'SUPPORT_SESSION_VIEW',
        'SUPPORT_ATTEMPT_VIEW', 'SUPPORT_ACCESS_VIEW', 'SUPPORT_PURCHASE_VIEW',
        'SUPPORT_PAYMENT_REF_VIEW', 'SUPPORT_NOTIFICATION_SEND', 'SUPPORT_SESSION_REVOKE',
        'SUPPORT_ATTEMPT_RECOVER')
WHERE r.code = 'SUPPORT'
ON CONFLICT (role_id, permission_id) DO NOTHING;
