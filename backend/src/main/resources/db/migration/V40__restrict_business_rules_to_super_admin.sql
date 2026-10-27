-- ---------------------------------------------------------------------------
-- V40 - Organization rules are authored by the SUPER_ADMIN only
--
-- Product decision (2026-10-26): business rules express platform-wide policy (the admin cap, the
-- reserved permission set). The organization administrator is a *subject* of those rules, not their
-- author, so it must not be able to edit them. V23 granted BUSINESS_RULE_VIEW/CREATE/UPDATE to
-- ADMIN; this migration removes the two write grants and leaves read access intact.
--
-- Why deactivate rather than delete the rows: role_permission carries an audit trail and a status
-- column precisely so a grant can be withdrawn without losing the history of it having existed.
-- Deleting would erase that record.
--
-- VIEW is kept deliberately. The organization administrator can still read what governs it — the
-- rules screen and the explanation cards stay useful — it simply cannot change them.
--
-- SUPER_ADMIN gains all three. V29 attached the PLATFORM_* codes; the rule codes were never part of
-- that set, so the owner could not author rules at all. That is the other half of the same defect:
-- restricting ADMIN without granting the owner would leave nobody able to edit a rule.
-- ---------------------------------------------------------------------------

-- Withdraw the write grants from ADMIN.
UPDATE role_permission rp
SET status = 'INACTIVE', updated_on = now()
FROM role r, permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.code = 'ADMIN'
  AND p.code IN ('BUSINESS_RULE_CREATE', 'BUSINESS_RULE_UPDATE')
  AND rp.status = 'ACTIVE';

-- Grant the full rule capability to the installation owner.
INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code IN ('BUSINESS_RULE_VIEW', 'BUSINESS_RULE_CREATE', 'BUSINESS_RULE_UPDATE')
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO UPDATE SET status = 'ACTIVE', updated_on = now();
