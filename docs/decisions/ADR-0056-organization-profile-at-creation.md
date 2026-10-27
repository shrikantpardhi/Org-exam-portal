# ADR-0056 — Collect the organization profile at creation and let the platform owner correct it

- **Status:** Proposed
- **Date:** 2026-10-27
- **Supersedes:** none
- **Amends:** `specs/05-API-Contract-V1.5.md` §72.2 (create request) and adds §72.17
- **Closes:** GAP-202 (profile unsettable at creation), GAP-203 (no platform-side profile edit)

## Context

`V37` (ADR-0051) added ten profile columns to `organization`: two address lines, city, state or
region, postal code, country, contact email, contact phone, website URL and logo URL. They were
deliberately NULLable, because the `PLATFORM` and `DEFAULT` organizations predate the profile and a
backfill would have had to invent values.

Two things were then missing, and together they made the columns almost unusable at the platform
tier:

1. **Creation ignored them.** `POST /platform/organizations` accepted only `code`, `name` and
   `description` (§72.2). An operator registering an institute knew its address and contact point at
   that moment, but had to create a bare record and fill the rest in later — so the rest was
   frequently never filled in.
2. **The platform owner could not edit them at all.** The only profile write in the system was
   `PUT /admin/organization-config/profile`, which is the *tenant* ADMIN editing *their own*
   organization, resolved from the principal. There was no platform-side write. A typo made during
   creation would have been permanently uncorrectable from the super-admin surface.

The columns were visible on the platform organization detail screen, which made the gap worse rather
than better: the screen displayed ten fields and offered no way to fill any of them.

## Decision

### 1. Creation accepts the whole profile

`POST /platform/organizations` gains the ten profile fields (§72.2). The `@Size` bounds mirror the
`V37` column widths exactly, so a value that passes validation can never be truncated or rejected by
PostgreSQL.

### 2. Only `code` and `name` remain required

Every profile column is NULLable by the ADR-0051 decision, and every existing row treats it as
optional. Making address or contact mandatory at the API boundary would (a) contradict the schema
decision without changing it, (b) reject an organization that legitimately has no public address, and
(c) require a backfill for the two rows that predate the profile. A requirement the data model does
not express is a UI preference masquerading as a rule. The form marks `code` and `name` as required
and leaves the rest visibly optional.

### 3. Blank and absent are the same stored value

`""`, whitespace-only and `null` all persist as `NULL`. Otherwise the same operator decision — "I
left this empty" — could produce two different stored states that render differently, and an
"empty" value would sort and filter unlike a null one.

### 4. A new endpoint corrects the profile

`PATCH /platform/organizations/{id}` (§72.17), permission `PLATFORM_ORGANIZATION_UPDATE` — the same
grant ADR-0055 gave a purpose. PATCH rather than PUT because the request is a set of edits, not a
whole-object replacement: an operator changing one phone number should not have to re-send nine
fields, and omitting a field must not be able to clear it by accident.

Empty string still **clears** a field. Without that, a stale contact detail could be created and
never removed; "omitted means unchanged" and "empty means cleared" are distinct and both needed.

### 5. The edit refuses to touch `code`, `status` or primacy

Each already has its own endpoint, permission and audit action: `code` is immutable, `status` belongs
to §72.16, primacy to §72.14. Folding any of them in would create a second path to the same state
with a different permission — exactly the escalation hole ADR-0054 had to close. Because the
application sets `fail-on-unknown-properties: true`, such a field is a `400`, not a silent partial
apply: the operator is told the field is wrong rather than believing it took effect.

### 6. `name` cannot be blank

`organization.name` is `NOT NULL`. "Clear the name" is not a meaningful operation, so a
whitespace-only `name` is refused while omitting it leaves it unchanged.

## Consequences

**Positive.** The profile can be captured when it is actually known, instead of deferred to a tenant
administrator who may never do it. A platform owner can fix a wrong address. The detail screen's ten
fields stop being read-only decoration. Both halves sit behind grants that already existed, so no new
permission and no migration are needed.

**Negative.** `CreateOrganizationRequest` grows to thirteen fields. A caller that omits them all is
unaffected (they are optional), but the request DTO is now the widest in the platform module. The
grouped form (Identity / Address / Contact) is what keeps it usable; a flat thirteen-field list would
not be.

**Neutral.** The tenant ADMIN can still edit their own organization's profile through
`/admin/organization-config/profile`, which is unchanged. Two writers with different scopes is
intended: the platform owner registers and corrects, the tenant owns day-to-day maintenance. Both
audit, with different actions (`ORGANIZATION_UPDATE` versus the admin-side action).

## Alternatives considered

- **Make address and contact required at creation.** Rejected per decision 2 — the schema, the
  existing rows and the ADR-0051 rationale all say nullable, and an institute without a public street
  address is a legitimate case.
- **PUT instead of PATCH.** Rejected: a whole-object replace forces the client to re-send every field
  it is not changing, and one forgotten field silently erases data. That failure mode is worse than
  the extra verb.
- **Let the platform owner use `PUT /admin/organization-config/profile`.** Rejected: that endpoint
  resolves the organization from the caller's own principal. A `SUPER_ADMIN` is not an ADMIN of any
  tenant, so it cannot address the organization being edited — the endpoint is structurally unable to
  do this job.
- **Add profile fields to `OrganizationSummary`.** Rejected: the list view shows code, name and
  status; adding ten columns to every row of a paginated list buys nothing and costs payload.
- **Require a valid URL scheme for `websiteUrl`/`logoUrl`.** Deferred. `logo_url` is specified as a
  URL (ADR-0051) and the frontend validates as a URL, but the backend keeps `@Size` only, matching
  the `V37` column being a plain `VARCHAR`. Tightening it would reject data the column accepts; a
  follow-up can add `@URL`-style validation once the storage module owns asset URLs.

## Verification

`PlatformOrganizationProfileIntegrationTest` (14 tests): a full profile round-trips through create and
detail; creation with only code and name succeeds and leaves every profile column null; a blank field
is stored as `NULL`; an invalid `contactEmail` is 400; an over-long field is 400; the platform owner
can correct name, city and contact email; an omitted field is left alone; an empty string clears a
field; a blank `name` is 400; `code`/`status` in the body are rejected with 400 and change nothing; an
unknown field is 400; the `PLATFORM` tenant is 404; an organization ADMIN is 403; an edit emits
`ORGANIZATION_UPDATE`.

Frontend: the create form renders all three field groups and submits an optional-fields-omitted
payload; the edit dialog is seeded from the current organization, closes on success, and keeps a
server error visible inside the dialog.

## Related

- ADR-0051 — organization profile columns (`V37`), which this record makes writable at the platform
  tier
- ADR-0055 — organization status lever; established that `PLATFORM_ORGANIZATION_UPDATE` is the
  platform write grant for an organization
- ADR-0054 — the escalation-hole reasoning reused in decision 5
- `specs/05-API-Contract-V1.5.md` §72.2, §72.17
- GAP-202, GAP-203 (closed)
