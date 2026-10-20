-- ---------------------------------------------------------------------------
-- V25 - Enable auto-submit on expiry for the default delivery policy
-- (P5 hardening, GAP-062).
--
-- The expiry auto-submit path is only taken for a CONFIGURED auto-submit
-- behaviour (specs/14-P5-Delivery-V1.5.md section 56). The seeded DEFAULT
-- delivery policy did not carry the key at all, so the parser fell back to the
-- built-in default of false and an expired attempt was never finalized.
--
-- This migration changes NO schema: it updates one JSONB configuration value on
-- the existing DEFAULT policy row, exactly as an administrator would through
-- the delivery policy API. The frozen schema, API contract and domain model are
-- untouched, and no table or column is created.
--
-- The idempotent form is used so re-running the migration is safe.
-- ---------------------------------------------------------------------------

UPDATE delivery_policy
   SET configuration = jsonb_set(configuration, '{autoSubmitOnExpiry}', 'true'::jsonb, true),
       updated_on = now()
 WHERE code = 'DEFAULT'
   AND NOT (configuration ? 'autoSubmitOnExpiry');
