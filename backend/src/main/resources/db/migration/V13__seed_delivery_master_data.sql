-- ---------------------------------------------------------------------------
-- V13 - Delivery operational permission catalog (P5-04/P5-25)
--
-- specs/14-P5-Delivery-V1.5.md §83 states that Delivery permission codes are "derived from the
-- Identity permission catalogue and are not invented in Delivery". The frozen Identity catalogue
-- defines no Delivery codes, so the minimal operational codes required by the P5 authorization
-- matrix are seeded here and recorded in docs/known-gaps.md (GAP-056).
--
-- Student runtime endpoints (start own attempt, read/save/submit own attempt) are authorized by
-- resource ownership and need no permission code; these codes gate the privileged staff actions.
-- ---------------------------------------------------------------------------

INSERT INTO permission (id, code, name, description, status, created_by, created_on) VALUES
    (gen_random_uuid(), 'DELIVERY_PREPARE_ATTEMPTS', 'Prepare attempts',      'Trigger attempt snapshot preparation for a schedule', 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'DELIVERY_ATTEMPT_VIEW_ANY',  'View any attempt',      'Read another student''s runtime attempt',             'ACTIVE', '00000000-0000-0000-0000-000000000001', now()),
    (gen_random_uuid(), 'DELIVERY_OPERATE',           'Operate delivery',      'Force operational session recovery or termination',   'ACTIVE', '00000000-0000-0000-0000-000000000001', now())
ON CONFLICT (code) DO NOTHING;

-- ADMIN: the full Delivery operational set.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r JOIN permission p ON p.code IN ('DELIVERY_PREPARE_ATTEMPTS', 'DELIVERY_ATTEMPT_VIEW_ANY', 'DELIVERY_OPERATE')
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- STAFF: operational support (no cross-user grading; view any attempt + prepare).
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r JOIN permission p ON p.code IN ('DELIVERY_PREPARE_ATTEMPTS', 'DELIVERY_ATTEMPT_VIEW_ANY', 'DELIVERY_OPERATE')
WHERE r.code = 'STAFF'
ON CONFLICT (role_id, permission_id) DO NOTHING;
