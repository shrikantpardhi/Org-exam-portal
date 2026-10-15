-- ---------------------------------------------------------------------------
-- V21 - Communication template catalog and permission seeds (P10-07, P10-19)
--
-- Seeds the initial email template catalog (specs/19-P10-Communication-V1.5.md §21, §23, §57) and
-- the Communication permission codes. The frozen Identity catalogue defines no Communication codes,
-- so the minimum set from §66/§67 is seeded here and recorded in docs/known-gaps.md (GAP-106).
--
-- Placeholders use the fixed {{variableName}} syntax (§24); rendering is a pure string replacement.
-- Every statement is idempotent.
-- ---------------------------------------------------------------------------

INSERT INTO email_template (id, code, name, subject, body, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'WELCOME', 'Welcome',
     'Welcome to the platform, {{studentName}}',
     '<p>Hello {{studentName}}, welcome to {{organizationName}}.</p>',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PASSWORD_RESET', 'Password reset',
     'Reset your password',
     '<p>Hello {{studentName}}, a password reset was requested for your account. If this was not you, ignore this message.</p>',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PURCHASE_CONFIRMATION', 'Purchase confirmation',
     'Your purchase is confirmed',
     '<p>Hello {{studentName}}, your purchase is confirmed.</p><p>{{purchaseSummary}}</p>',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PAYMENT_FAILED', 'Payment failed',
     'Your payment could not be completed',
     '<p>Hello {{studentName}}, your payment could not be completed. You can retry from your purchases.</p>',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PAYMENT_REFUNDED', 'Payment refunded',
     'Your payment has been refunded',
     '<p>Hello {{studentName}}, your payment has been refunded and access has been revoked.</p>',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'RESULT_PUBLISHED', 'Result published',
     'Your result is available',
     '<p>Hello {{studentName}}, your result for {{assessmentName}} is now available.</p>',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ASSESSMENT_SCHEDULED', 'Assessment scheduled',
     'An assessment has been scheduled',
     '<p>Hello {{studentName}}, {{assessmentName}} has been scheduled.</p>',
     'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'COMMUNICATION_TEMPLATE_READ',   'Read email templates',   'View email templates',              'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'COMMUNICATION_TEMPLATE_WRITE',  'Write email templates',  'Create and update email templates', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'COMMUNICATION_TEMPLATE_DELETE', 'Delete email templates', 'Archive email templates',           'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ANNOUNCEMENT_READ',    'Read announcements',    'View announcements',            'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ANNOUNCEMENT_WRITE',   'Write announcements',   'Create and update announcements', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ANNOUNCEMENT_PUBLISH', 'Publish announcements', 'Publish an announcement',        'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ANNOUNCEMENT_DELETE',  'Delete announcements',  'Archive announcements',          'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- ADMIN: the full Communication capability set.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('COMMUNICATION_TEMPLATE_READ', 'COMMUNICATION_TEMPLATE_WRITE',
        'COMMUNICATION_TEMPLATE_DELETE', 'ANNOUNCEMENT_READ', 'ANNOUNCEMENT_WRITE',
        'ANNOUNCEMENT_PUBLISH', 'ANNOUNCEMENT_DELETE')
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- STAFF: announcements and read-only templates.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('COMMUNICATION_TEMPLATE_READ', 'ANNOUNCEMENT_READ', 'ANNOUNCEMENT_WRITE',
        'ANNOUNCEMENT_PUBLISH', 'ANNOUNCEMENT_DELETE')
WHERE r.code = 'STAFF'
ON CONFLICT (role_id, permission_id) DO NOTHING;
