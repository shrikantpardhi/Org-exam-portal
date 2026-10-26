# SUPER_ADMIN Flow — screen-by-screen

Scope: the installation owner. Every screen below belongs to the `/platform/*` tree except
the shared `/login` and `/change-password`. Screens are listed in the order a fresh install
encounters them, then the full menu.

Screen template: URL, purpose, entry, exit, wireframe, API, permissions, state machine, actions,
open questions.

---

## 1. Login

- **URL:** `/login/admin`
- **Purpose:** authenticate the SUPER_ADMIN against the System Admin portal.
- **Entry:** unauthenticated visit to any protected route (redirect here with `next`), or direct.
- **Exit:** success → /platform or `/change-password` when `mustChangePassword`; `next` wins when safe. A `401 PORTAL_MISMATCH` shows the generic "this account cannot use this portal".
- **Wireframe:**

      +--------------------------------+
      |   [logo]  Platform             |
      |   Email    [________________]  |
      |   Password [________________]  |
      |   [ Sign in ]                  |
      |   Forgot password?             |
      +--------------------------------+

- **API:** POST /api/v1/auth/login with { username, password, portal: "SYSTEM_ADMIN" } (ADR-0035).
- **Permissions:** none.
- **State machine:** idle → submitting → error(invalid credentials / locked) → success.
- **Actions:** sign in; link to forgot-password.
- **Open questions:** none (existing screen, restyled to shell tokens).

---

## 2. Change password (forced)

- **URL:** `/change-password`
- **Purpose:** forced first-login password change (ADR-0034); also reachable from the avatar menu.
- **Entry:** `mustChangePassword=true` (redirected here from anywhere), or avatar → Change password.
- **Exit:** success → role home (SUPER_ADMIN → /platform); cancel → sign out.
- **Wireframe:**

      +--------------------------------+
      |  Change your password          |
      |  Current password [_________]  |
      |  New password     [_________]  |
      |  Confirm          [_________]  |
      |  [ Update password ]  [Sign out] |
      +--------------------------------+

- **API:** POST /api/v1/auth/change-password (§72.1).
- **Permissions:** authenticated.
- **State machine:** idle → submitting → error(401 wrong current / 400 policy) → success →
  refetch `/me` → redirect home.
- **Actions:** update; sign out.
- **Open questions:** whether to also enforce a minimum-password-strength meter client-side
  (backend policy is authoritative; meter is UX only).

---

## 3. Platform dashboard — empty (no organization)

- **URL:** `/platform`
- **Purpose:** landing for a fresh install: prompt to create the first business organization.
- **Entry:** SUPER_ADMIN login, no business org exists yet.
- **Exit:** → create organization; → providers; → system info.
- **Wireframe:**

      +--------------------------------------------------+
      | Platform                                  [avatar]|
      +--------------------------------------------------+
      |  Welcome. No business organization yet.          |
      |  Create the first organization and its ADMIN.    |
      |  [ + Create organization ]                       |
      |                                                  |
      |  (system info card: version, migration)          |
      +--------------------------------------------------+

- **API:** GET /api/v1/platform/organizations (count) and GET /api/v1/platform/system (summary).
- **Permissions:** PLATFORM_ORGANIZATION_VIEW, PLATFORM_SYSTEM_VIEW.
- **State machine:** loading → empty (no org) → actionable; error → retry card.
- **Actions:** create organization; open system info; open providers.
- **Open questions:** whether the empty state should block the rest of the platform menu or
  merely highlight it (design says: menu stays visible; only the primary CTA is emphasized).

---

## 4. Platform dashboard — with organization

- **URL:** `/platform`
- **Purpose:** landing once ≥1 business org exists.
- **Entry:** SUPER_ADMIN login, ≥1 business org.
- **Exit:** → organization list / detail; → create another org.
- **Wireframe:**

      +--------------------------------------------------+
      | Platform                                  [avatar]|
      +--------------------------------------------------+
      |  Organizations         1                         |
      |  Administrators        1                         |
      |  [ + Create organization ]                       |
      +--------------------------------------------------+

- **API:** GET /api/v1/platform/organizations (aggregate counts), GET /api/v1/platform/status.
- **Permissions:** PLATFORM_ORGANIZATION_VIEW, PLATFORM_STATUS_VIEW.
- **State machine:** loading → populated → actionable; error → retry.
- **Actions:** create org; open org detail; open status.
- **Open questions:** none.

---

## 5. Organizations list

- **URL:** `/platform/organizations`
- **Purpose:** all business organizations (PLATFORM excluded).
- **Entry:** sidebar / dashboard.
- **Exit:** → create org; → org detail.
- **Wireframe:**

      +--------------------------------------------------+
      | Organizations            [search]  [+ Create]     |
      |  code  name   status   admins   users   updated   |
      |  ACME  Acme   ACTIVE   1        12      ...       |
      +--------------------------------------------------+

- **API:** GET /api/v1/platform/organizations (§72.3; server pagination, filters).
- **Permissions:** PLATFORM_ORGANIZATION_VIEW; create CTA requires PLATFORM_ORGANIZATION_CREATE.
- **State machine:** loading → populated / empty → error.
- **Actions:** filter, paginate, create, open row.
- **Open questions:** none.

---

## 6. Create organization

- **URL:** `/platform/organizations/new`
- **Purpose:** create a business organization (ADR-0033).
- **Entry:** list or dashboard CTA.
- **Exit:** success → org detail (next: create ADMIN); cancel → list.
- **Wireframe:**

      +--------------------------------+
      |  Create organization           |
      |  Code     [________] (unique)  |
      |  Name     [________]           |
      |  Description [________________]|
      |  [ Create ]  [ Cancel ]        |
      +--------------------------------+

- **API:** POST /api/v1/platform/organizations (§72.2).
- **Permissions:** PLATFORM_ORGANIZATION_CREATE.
- **State machine:** idle → submitting → error(400 / 409 duplicate code) → success → redirect detail.
- **Actions:** create; cancel.
- **Open questions:** none.

---

## 7. Organization detail — no admin

- **URL:** `/platform/organizations/[id]`
- **Purpose:** a created org before its ADMIN exists.
- **Entry:** create success, or list row.
- **Exit:** → create ADMIN; → back to list.
- **Wireframe:**

      +--------------------------------------------------+
      |  Acme (ACME)              [Create administrator] |
      |  No administrator assigned yet.                  |
      +--------------------------------------------------+

- **API:** GET /api/v1/platform/organizations/{id} (§72.4).
- **Permissions:** PLATFORM_ORGANIZATION_VIEW; create ADMIN CTA requires PLATFORM_ADMIN_CREATE.
- **State machine:** loading → detail(adminCount=0) → prompt; error → 404 card.
- **Actions:** create admin; back.
- **Open questions:** none.

---

## 8. Organization detail — with admin

- **URL:** `/platform/organizations/[id]`
- **Purpose:** a created org with its single ADMIN.
- **Entry:** list row or create-admin success.
- **Exit:** → list; (no admin edit in V1.5 — see open questions).
- **Wireframe:**

      +--------------------------------------------------+
      |  Acme (ACME)                                     |
      |  Administrator: Jane Doe (jane@acme.example)     |
      |  users: 12    created: ...                       |
      +--------------------------------------------------+

- **API:** GET /api/v1/platform/organizations/{id} (§72.4).
- **Permissions:** PLATFORM_ORGANIZATION_VIEW.
- **State machine:** loading → detail(adminCount≥1) → populated; error → 404.
- **Actions:** back; (no edit).
- **Open questions:** SUPER_ADMIN edit of an org or its ADMIN is not in the 9 endpoints; confirm
  it is deferred rather than omitted by mistake.

---

## 9. Create ADMIN

- **URL:** `/platform/organizations/[id]/admins/new`
- **Purpose:** create the single ADMIN for a business org (ADR-0032 MAX_ADMINS_PER_ORG=1).
- **Entry:** org detail CTA.
- **Exit:** success → org detail (with admin); cancel → detail.
- **Wireframe:**

      +--------------------------------+
      |  Create administrator          |
      |  First name [________]         |
      |  Last name  [________]         |
      |  Email      [________]         |
      |  Mobile     [________]         |
      |  [ Create and send invite ]    |
      +--------------------------------+

- **API:** POST /api/v1/platform/organizations/{id}/admins (§72.5).
- **Permissions:** PLATFORM_ADMIN_CREATE.
- **State machine:** idle → submitting → error(409 cap / 409 dup / 404 org) → success → detail.
- **Actions:** create; cancel.
- **Open questions:** invitation copy (subject/body) sourced from the USER_INVITATION template
  (ADR-0034) — confirm wording lives in backend seed data, not the UI.

---

## 10. Provider configuration

- **URL:** `/platform/providers`
- **Purpose:** email and payment provider config (ADR-0033 C5, stored in organization_setting).
- **Entry:** sidebar.
- **Exit:** save / cancel.
- **Wireframe:**

      +--------------------------------------------------+
      | Providers                                       |
      |  [NOT IMPLEMENTED IN V1.5 banner]               |
      |  Email   provider: reference   configured: yes  |
      |  Payment provider: reference   configured: no   |
      |  (values read-only in V1.5)                     |
      +--------------------------------------------------+

- **API:** GET /api/v1/platform/providers (§72.8); PUT (§72.9) shown but disabled behind the banner.
- **Permissions:** PLATFORM_PROVIDER_VIEW (read); PLATFORM_PROVIDER_UPDATE (write, gated).
- **State machine:** loading → read-only snapshot; error → retry. Write path is disabled.
- **Actions:** (none active beyond view) + banner dismiss.
- **Open questions:** none — GAP-099/GAP-108 defer real provider adapters; UI is a read-only
  "not implemented in V1.5" panel.

---

## 11. System information

- **URL:** `/platform/system`
- **Purpose:** application/migration/dependency health (ADR-0033 C6).
- **Entry:** sidebar.
- **Exit:** none (read-only).
- **Wireframe:**

      +--------------------------------------------------+
      | System information                              |
      |  app version    1.5.0                           |
      |  frontend       1.5.0                           |
      |  migration      V30 (applied ...)               |
      |  uptime         ...                             |
      |  dependencies   postgres UP                    |
      +--------------------------------------------------+

- **API:** GET /api/v1/platform/system (§72.7; gitCommit/buildDate nullable, no container list).
- **Permissions:** PLATFORM_SYSTEM_VIEW.
- **State machine:** loading → populated; error → retry.
- **Actions:** refresh.
- **Open questions:** none.

---

## 12. Seeds — roles

- **URL:** `/platform/seeds/roles`
- **Purpose:** read-only view of the role catalogue including SUPER_ADMIN.
- **Entry:** sidebar.
- **Exit:** none.
- **Wireframe:**

      +--------------------------------------------------+
      | Roles                                           |
      |  code         name                 status       |
      |  SUPER_ADMIN  Super Administrator   ACTIVE      |
      |  ADMIN        Administrator         ACTIVE      |
      |  ...                                           |
      +--------------------------------------------------+

- **API:** existing role list (P11 role read path).
- **Permissions:** PLATFORM_SYSTEM_VIEW (screen gate) **and ROLE_VIEW** (the read itself,
  `specs/10-P1-Identity-V1.5.md` §68). Granted to SUPER_ADMIN by `V32` (D-01, ADR-0032 amendment);
  no create/update/delete in V1.5.
- **State machine:** loading → populated; error → retry.
- **Actions:** none (view).
- **Open questions:** confirm SUPER_ADMIN appears in the shared role read path (it is seeded by
  V29); if the role list filters by org, the platform view must be org-agnostic.

---

## 13. Seeds — permissions

- **URL:** `/platform/seeds/permissions`
- **Purpose:** read-only view of the permission catalogue incl. the PLATFORM_* set.
- **Entry:** sidebar.
- **Exit:** none.
- **Wireframe:**

      +--------------------------------------------------+
      | Permissions                                     |
      |  code                             status        |
      |  PLATFORM_ORGANIZATION_CREATE     ACTIVE        |
      |  PLATFORM_ADMIN_CREATE            ACTIVE        |
      |  USER_VIEW                        ACTIVE        |
      +--------------------------------------------------+

- **API:** existing permission list.
- **Permissions:** PLATFORM_SYSTEM_VIEW (screen gate) **and PERMISSION_VIEW** (the read itself,
  `specs/10-P1-Identity-V1.5.md` §68). Granted to SUPER_ADMIN by `V32` (D-01, ADR-0032 amendment).
- **State machine:** loading → populated; error → retry.
- **Actions:** none (view).
- **Open questions:** none.

---

## 14. Seeds — lookups

- **URL:** `/platform/seeds/lookups`
- **Purpose:** read-only view of lookup seed data.
- **Entry:** sidebar.
- **Exit:** none.
- **Wireframe:**

      +--------------------------------------------------+
      | Lookups                                         |
      |  category   code        label          status   |
      |  ...                                            |
      +--------------------------------------------------+

- **API:** existing lookup list (V23 seeds).
- **Permissions:** PLATFORM_SYSTEM_VIEW.
- **State machine:** loading → populated / empty → error.
- **Actions:** none (view).
- **Open questions:** none.

---

## 15. Multi-org placeholder

- **URL:** `/platform/multi-org`
- **Purpose:** disabled placeholder (ADR-0033: multi-org is V2).
- **Entry:** sidebar (disabled item).
- **Exit:** back.
- **Wireframe:**

      +--------------------------------------------------+
      | Multi-organization                              |
      |  This feature is planned for V2.                |
      +--------------------------------------------------+

- **API:** none.
- **Permissions:** PLATFORM_ORGANIZATION_VIEW (view-only placeholder).
- **State machine:** static.
- **Actions:** back.
- **Open questions:** none.

---

## 16. Profile

- **URL:** `/platform/profile`
- **Purpose:** view/edit own profile (name, email, mobile).
- **Entry:** avatar → Profile.
- **Exit:** save / back.
- **Wireframe:**

      +--------------------------------+
      |  Profile                       |
      |  First name [________]         |
      |  Last name  [________]         |
      |  Email      [________]         |
      |  Mobile     [________]         |
      |  [ Save ]                      |
      +--------------------------------+

- **API:** existing `/me` read + profile update (P1 self-service path).
- **Permissions:** authenticated.
- **State machine:** loading → edit → submitting → error / success.
- **Actions:** save; back.
- **Open questions:** none.

---

## 17. Change password (profile context)

- **URL:** `/platform/change-password`
- **Purpose:** voluntary password rotation (not forced).
- **Entry:** avatar → Change password.
- **Exit:** success → profile; cancel → back.
- **Wireframe:** same as screen 2.
- **API:** POST /api/v1/auth/change-password (§72.1).
- **Permissions:** authenticated.
- **State machine:** idle → submitting → error / success.
- **Actions:** update; cancel.
- **Open questions:** none (reuses the shared forced-change screen with a "Cancel" instead of
  "Sign out" when not forced).
