-- ---------------------------------------------------------------------------
-- V42 - Raise the organization administrator cap to five (ADR-0054)
--
-- A previous turn shipped the primary-administrator rule (ADR-0052) and the platform CRUD
-- endpoints (ADR-0053), but left MAX_ADMINS_PER_ORG at its original value of 1. With a cap of one,
-- an organization can only ever hold a single administrator, which makes both features moot:
-- there is never a second administrator to designate as primary, to give extra roles to, or to
-- transfer primacy between. Product direction (2026-10-27) is that organizations may hold several
-- administrators with one of them designated primary.
--
-- Why an UPDATE and not a new rule row:
--   * The code, its meaning, and every reader of it are unchanged; only the number moves.
--   * `business_rule` has UNIQUE(code), so inserting a second MAX_ADMINS_PER_ORG row is impossible
--     by design.
--   * V29 already seeded this row; superseding its value in place keeps one authoritative number
--     rather than a history of overlapping rows that every reader would have to sort through.
--
-- Reverting is the same statement with 1.
--
-- Note for the next reader: the cap is *enforced* in two places
-- (`PlatformOrganizationService.createAdmin` and `UserService.guardAdminCap`), but this row is the
-- single source of the number. Neither enforcer hard-codes a fallback of five; both fall back to
-- their own default only when the rule is missing.
-- ---------------------------------------------------------------------------

UPDATE business_rule
SET configuration = '{"maxAdmins": 5}',
    updated_on = now()
WHERE code = 'MAX_ADMINS_PER_ORG';

COMMENT ON TABLE business_rule IS
    'Configurable platform policy. MAX_ADMINS_PER_ORG carries {"maxAdmins": N}; raised from 1 to 5 by V42 (ADR-0054) so an organization can hold several administrators with one designated primary.';
