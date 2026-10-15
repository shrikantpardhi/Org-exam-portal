-- ---------------------------------------------------------------------------
-- V19 - Commerce permission catalog (P9-20/P9-21)
--
-- specs/18-P9-Commerce-V1.5.md §30 defines the authorization matrix but the frozen Identity
-- catalogue defines no Commerce codes. The minimal codes required by the matrix are seeded here
-- and recorded in docs/known-gaps.md (GAP-097), following the GAP-056/070/089 precedent.
--
-- Student purchase/access reads are self-scoped by ownership (§67) and need no permission code.
-- Every statement is idempotent.
-- ---------------------------------------------------------------------------

INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'ACCESS_PACKAGE_VIEW',    'View access packages',    'Browse access packages and their collection mapping', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ACCESS_PACKAGE_MANAGE',  'Manage access packages',  'Create, update and archive access packages',          'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUBSCRIPTION_PLAN_VIEW',   'View subscription plans', 'Browse subscription plans',                         'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'SUBSCRIPTION_PLAN_MANAGE', 'Manage subscription plans', 'Create, update and archive subscription plans',   'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'COUPON_VIEW',   'View coupons',   'Browse coupons',                          'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'COUPON_MANAGE', 'Manage coupons', 'Create, update and archive coupons',      'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PURCHASE_VIEW',   'View purchases',   'Read any purchase (finance/admin scope)', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'PURCHASE_REFUND', 'Refund purchases', 'Refund a purchase and revoke access',     'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ACCESS_GRANT_VIEW',   'View access grants',   'Read access grants',                'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'ACCESS_GRANT_MANAGE', 'Manage access grants', 'Create and revoke manual grants',    'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- ADMIN: the full Commerce capability set.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('ACCESS_PACKAGE_VIEW', 'ACCESS_PACKAGE_MANAGE', 'SUBSCRIPTION_PLAN_VIEW',
        'SUBSCRIPTION_PLAN_MANAGE', 'COUPON_VIEW', 'COUPON_MANAGE', 'PURCHASE_VIEW', 'PURCHASE_REFUND',
        'ACCESS_GRANT_VIEW', 'ACCESS_GRANT_MANAGE')
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- FINANCE: purchases, refunds, coupons and the catalog.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('ACCESS_PACKAGE_VIEW', 'ACCESS_PACKAGE_MANAGE', 'SUBSCRIPTION_PLAN_VIEW',
        'SUBSCRIPTION_PLAN_MANAGE', 'COUPON_VIEW', 'COUPON_MANAGE', 'PURCHASE_VIEW', 'PURCHASE_REFUND',
        'ACCESS_GRANT_VIEW')
WHERE r.code = 'FINANCE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- STAFF: browse the catalog and provision manual grants (§30).
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('ACCESS_PACKAGE_VIEW', 'SUBSCRIPTION_PLAN_VIEW', 'ACCESS_GRANT_VIEW',
        'ACCESS_GRANT_MANAGE')
WHERE r.code = 'STAFF'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- SUPPORT: browse the catalog and provision manual grants (§42).
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('ACCESS_PACKAGE_VIEW', 'SUBSCRIPTION_PLAN_VIEW', 'ACCESS_GRANT_VIEW',
        'ACCESS_GRANT_MANAGE')
WHERE r.code = 'SUPPORT'
ON CONFLICT (role_id, permission_id) DO NOTHING;
