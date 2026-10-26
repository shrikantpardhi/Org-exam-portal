-- ---------------------------------------------------------------------------
-- V37 - Organization profile fields (ADR-0051)
--
-- The organization detail screens need more than code/name/description: an operating
-- institute records its address, a contact point, and a logo. specs/04-Database-Schema
-- §25.2 defines `organization` with only the identity columns, so this is an amendment to a
-- frozen table and is recorded in ADR-0051 exactly like V35/V36 were in ADR-0047.
--
-- Design notes:
--   * Every column is NULLable. Existing rows (the PLATFORM system organization and the DEFAULT
--     business organization) predate the profile and must stay valid without a backfill.
--   * `logo_url` is a URL, not binary data: assets are served by the storage module, and storing
--     bytes in the organization row would bloat every read of a hot table.
--   * `contact_email` is deliberately not constrained to be unique — two organizations may share a
--     shared services inbox — but it is length-bounded like every other address in the schema.
--   * No index is added: none of these columns is a lookup key or a sort key.
-- ---------------------------------------------------------------------------

ALTER TABLE organization
    ADD COLUMN IF NOT EXISTS address_line1 VARCHAR(200),
    ADD COLUMN IF NOT EXISTS address_line2 VARCHAR(200),
    ADD COLUMN IF NOT EXISTS city          VARCHAR(100),
    ADD COLUMN IF NOT EXISTS state_region  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS postal_code   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS country       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(254),
    ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS website_url   VARCHAR(500),
    ADD COLUMN IF NOT EXISTS logo_url      VARCHAR(500);

COMMENT ON COLUMN organization.address_line1 IS 'Organization profile (V37, ADR-0051). Nullable: pre-existing rows have no profile.';
COMMENT ON COLUMN organization.logo_url      IS 'Absolute or site-relative URL to a stored asset; never inline binary data.';
