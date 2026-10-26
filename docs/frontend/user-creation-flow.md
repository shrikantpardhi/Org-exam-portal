# User-creation flow — SUPER_ADMIN vs ADMIN

The two create-user surfaces differ by role, by permitted role set, and by transport. This
document pins the differences so the implementation of Step 4–6 cannot drift.

## Side by side

| Dimension | SUPER_ADMIN creates ADMIN | ADMIN creates STAFF/REVIEWER/SUPPORT/FINANCE/STUDENT |
|---|---|---|
| URL | `/platform/organizations/[id]/admins/new` | `/admin/users/new` |
| Endpoint | POST /api/v1/platform/organizations/{id}/admins (§72.5) | existing P1 user-create |
| Permission | PLATFORM_ADMIN_CREATE | USER_CREATE |
| Target role | ADMIN (exactly one, MAX_ADMINS_PER_ORG=1) | multi-select from STAFF / REVIEWER / SUPPORT / FINANCE / STUDENT |
| Excluded roles | SUPER_ADMIN | ADMIN, SUPER_ADMIN |
| Org binding | the org in the URL (created by SUPER_ADMIN) | the signed-in ADMIN's own org (server-side, never client-supplied) |
| Field set | firstName, lastName, email, mobile | firstName, lastName, email, mobile, roles (multi), status |
| First password | never shown/sent; reset link emailed (ADR-0034) | never shown/sent; reset link emailed (ADR-0034) |
| must_change_password | true | true |
| Cap enforcement | business_rule MAX_ADMINS_PER_ORG=1 (409 when reached) | none (no cap on non-ADMIN roles) |
| Duplicate handling | 409 (email or mobile) | 409 (email or mobile) |
| Audit | ORGANIZATION_CREATE / USER_CREATE / ADMIN_ASSIGN | USER_CREATE (+ role assignments) |

## Shared invariants

1. **Backend authoritative.** The UI never decides the target org or the permitted role set;
   the server derives the org from the caller and rejects any disallowed role (403/422).
2. **No password in transit or on screen.** First credentials are delivered as a reset link
   (ADR-0034, GAP-002 closed). The create form has no password field.
3. **must_change_password=true** for every created account. The reset link is the
   first-password setup and clears the flag; the shared `/change-password` gate applies to the
   bootstrap SUPER_ADMIN (whose password is known), not to API-created accounts.
4. **Email/mobile uniqueness** is enforced server-side (409), surfaced as field errors.
5. **Role options are permission-driven**, not hard-coded: the ADMIN form lists only roles the
   caller may assign; the SUPER_ADMIN form hard-codes nothing — it targets ADMIN by construction.

## Sequence — SUPER_ADMIN

    login → /platform → create org → org detail → create ADMIN → invite email
       → ADMIN /reset-password → set password → /admin

## Sequence — ADMIN

    login → /admin → users → create user (roles) → invite email
       → user /reset-password → set password → role landing

## Validation map (client mirrors, server enforces)

| Field | Rule | Error code |
|---|---|---|
| firstName / lastName | required, trimmed, ≤ limits | VALIDATION_ERROR |
| email | required, valid email, unique | VALIDATION_ERROR / CONFLICT |
| mobile | optional, unique when present | VALIDATION_ERROR / CONFLICT |
| roles | non-empty, ⊆ permitted set | VALIDATION_ERROR / AUTHORIZATION_ERROR |
| status | ACTIVE (default) | — |
| ADMIN cap | org already has an active ADMIN | CONFLICT |
