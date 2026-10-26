-- ---------------------------------------------------------------------------
-- V30 - First-login password-change flag (ADR-0034, C4)
--
-- Every user whose first credential is delivered out-of-band (bootstrap SUPER_ADMIN,
-- and any account created with a first password sent by email) must change that
-- password on first login. This column is the durable flag the auth filter reads to
-- force that change (ADR-0034).
--
-- Additive only: existing rows default to FALSE, so no deployed account is forced to
-- change its password by this migration alone.
-- ---------------------------------------------------------------------------

ALTER TABLE "user" ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
