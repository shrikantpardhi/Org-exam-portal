# ADR-0051 - Organization profile columns

- **Status:** Proposed
- **Date:** 2026-10-26
- **Amends:** [`specs/04-Database-Schema-V1.5.md`](../specs/04-Database-Schema-V1.5.md) §25.2 (`organization`)
- **Related:** ADR-0033 (platform organizations), ADR-0037 (admin configuration), ADR-0047 (prior
  frozen-schema amendments)

## Context

`organization` was defined with identity columns only: `code`, `name`, `description`, `status`. That
was sufficient while the only consumer was the sign-in flow, which needs a name to display.

The organization settings screen and the platform organization detail screen now need the
organization to describe itself the way a real institute does: a title, a description, a postal
address, a contact point, a website and a logo. Without those columns:

- an administrator has no way to record them, and
- the platform owner cannot see who to contact when an organization reports a problem.

The alternative was to store the profile in `organization_setting` under new registered keys.
`OrganizationSettingRegistry` is a closed allow-list whose javadoc states that no module silently
invents a configuration key, and the document is a JSON blob validated per key. These are stable,
queryable, first-class attributes of the organization itself, not runtime configuration: the platform
organization list may reasonably want to sort or filter by them, and `organization_setting` is a
per-organization opaque document. Storing them there would make the organization row depend on a JSON
document to be complete.

## Decision

Add ten nullable columns to `organization` via Flyway `V37__organization_profile.sql`:

| Column | Type | Notes |
|---|---|---|
| `address_line1` | `VARCHAR(200)` | |
| `address_line2` | `VARCHAR(200)` | |
| `city` | `VARCHAR(100)` | |
| `state_region` | `VARCHAR(100)` | |
| `postal_code` | `VARCHAR(20)` | |
| `country` | `VARCHAR(100)` | |
| `contact_email` | `VARCHAR(254)` | not unique: organizations may share a services inbox |
| `contact_phone` | `VARCHAR(32)` | |
| `website_url` | `VARCHAR(500)` | |
| `logo_url` | `VARCHAR(500)` | a URL, never inline binary data |

Every column is nullable. The `PLATFORM` system organization and the `DEFAULT` business organization
predate the profile, and a migration that required a backfill would have to invent values for them.
No index is added: none of these columns is a lookup key or a sort key.

## API

One new endpoint, following the existing organization-configuration controller:

```
PUT /api/v1/admin/organization-config/profile      ORGANIZATION_SETTING_UPDATE
```

The organization is resolved from the authenticated principal, never from a path or body identifier,
so an administrator cannot address another tenant. The read side is folded into the existing
`GET /api/v1/admin/organization-config` response (`organization` object) rather than adding a second
read: the settings screen should not need two requests to render one page. `GET
/api/v1/platform/organizations/{id}` also carries the profile for the platform owner.

No frozen request or response **shape** is changed: `OrganizationConfigOrganization` and
`OrganizationDetail` gain fields, and both are additive. Existing clients that ignore unknown JSON
fields are unaffected.

## Consequences

- `specs/04` §25.2 and `specs/05` must record the added columns and the new endpoint before V1.5 is
  declared frozen again. **That specification edit is not made by this change** — it is recorded here
  so it is a deliberate, reviewable decision rather than an undocumented drift, matching how ADR-0047
  handled `V35`/`V36`.
- `organization` is now slightly wider on every read. Ten nullable varchars are negligible next to the
  existing `description text`.
- A future multi-organization V2 can use these columns for an organization directory without a second
  migration.
- Logo storage is deliberately a URL. Binary uploads would need the storage module, and putting bytes
  in a hot identity table would slow every sign-in lookup.
