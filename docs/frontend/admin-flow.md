# ADMIN Flow — screen-by-screen

Scope: the business-organization administrator. Every screen below belongs to the `/admin/*`
tree except the shared `/login`, `/reset-password`, and `/change-password`. **User
management is the priority** (per product-owner scope); the remaining screens are specified as
reachable but non-focus.

Screen template: URL, purpose, entry, exit, wireframe, API, permissions, state machine, actions,
open questions.

---

## 1. Login

URL `/login/organization`; sends `portal: "ORGANIZATION_ADMIN"` (ADR-0035). On
success an ADMIN lands on `/admin` (or `/change-password` when forced). A
`401 PORTAL_MISMATCH` shows the generic "this account cannot use this portal".

---

## 2. Reset password (invitation)

- **URL:** `/reset-password`
- **Purpose:** accept the invitation/reset link delivered by email (ADR-0034) and set the first password.
- **Entry:** email link with a signed reset token.
- **Exit:** success → `/login` ("password set, sign in"); expiry → re-request.
- **Wireframe:**

      +--------------------------------+
      |  Set your password             |
      |  New password     [_________]  |
      |  Confirm          [_________]  |
      |  [ Set password ]              |
      +--------------------------------+

- **API:** existing P1 reset-complete endpoint (token from email).
- **Permissions:** none (token-bound).
- **State machine:** idle → validating token → form → submitting → error(expired/invalid) → success.
- **Actions:** set; re-request link.
- **Open questions:** none (reuses P1 reset flow; ADR-0034 only adds the email transport).

---

## 3. Admin dashboard

- **URL:** `/admin`
- **Purpose:** landing with organization summary.
- **Entry:** login.
- **Exit:** → users; → other sections.
- **Wireframe:**

      +--------------------------------------------------+
      | Admin                                   [avatar] |
      +--------------------------------------------------+
      |  Users (active)     42                          |
      |  Quick action: [ + Create user ]                |
      +--------------------------------------------------+

- **API:** existing org-summary / counts (P11 dashboard path).
- **Permissions:** authenticated (counts reflect permitted scopes).
- **State machine:** loading → populated; error → retry.
- **Actions:** create user; open users.
- **Open questions:** none.

---

## 4. Users list

- **URL:** `/admin/users`
- **Purpose:** the priority screen: manage organization users (ADMIN can create STAFF / REVIEWER / SUPPORT / FINANCE / STUDENT, not ADMIN/SUPER_ADMIN).
- **Entry:** sidebar / dashboard.
- **Exit:** → create; → detail; → edit; → archive.
- **Wireframe:**

      +--------------------------------------------------+
      | Users            [search] [role▾] [status▾] [+New]|
      |  name        email        roles        status    |
      |  Jane Doe    jane@...     ADMIN        ACTIVE    |
      |  Bob Ray     bob@...      STAFF        ACTIVE    |
      +--------------------------------------------------+

- **API:** existing P1/P11 user list (server pagination, filters, sorting).
- **Permissions:** USER_VIEW; create CTA requires USER_CREATE.
- **State machine:** loading → populated / empty → error; row actions → confirm → success/error.
- **Actions:** filter, paginate, create, open, edit, archive.
- **Open questions:** confirm the user list exposes the ADMIN row (read-only, no archive) without
  allowing self-demotion (P11 §18: last-ADMIN invariant).

---

## 5. Create user

- **URL:** `/admin/users/new`
- **Purpose:** create a non-privileged user (STAFF/REVIEWER/SUPPORT/FINANCE/STUDENT).
- **Entry:** list CTA.
- **Exit:** success → list (with toast); cancel → list.
- **Wireframe:**

      +--------------------------------------------------+
      |  Create user                                    |
      |  First name [________]   Last name [________]   |
      |  Email      [________]   Mobile    [________]   |
      |  Roles (multi) [STAFF] [REVIEWER] ...           |
      |  Status     (ACTIVE)                            |
      |  [ Create and send invite ]  [ Cancel ]         |
      +--------------------------------------------------+

- **API:** existing P1 user-create; invitation/reset delivery per ADR-0034.
- **Permissions:** USER_CREATE; role options are capped to the creatable set (ADMIN/SUPER_ADMIN excluded).
- **State machine:** idle → submitting → error(409 dup email/mobile, 400 role) → success.
- **Actions:** create; cancel.
- **Open questions:** field set frozen to firstName/lastName/email/mobile/roles(multi)/status —
  confirm no department/manager fields are shown.

---

## 6. User detail

- **URL:** `/admin/users/[id]`
- **Purpose:** read a single user.
- **Entry:** list row.
- **Exit:** → edit; → back.
- **Wireframe:**

      +--------------------------------------------------+
      |  Jane Doe            ACTIVE          [Edit]      |
      |  email  jane@acme.example                        |
      |  mobile ...                                     |
      |  roles  ADMIN                                   |
      +--------------------------------------------------+

- **API:** existing user read.
- **Permissions:** USER_VIEW.
- **State machine:** loading → detail; error → 404/retry.
- **Actions:** edit; back.
- **Open questions:** none.

---

## 7. Edit user

- **URL:** `/admin/users/[id]/edit`
- **Purpose:** edit roles/status (and name/email/mobile) for an existing user.
- **Entry:** detail / list.
- **Exit:** save → detail; cancel → detail.
- **Wireframe:** same fields as create, prefilled.
- **API:** existing user update; role assignment enforces the creatable-set cap and last-ADMIN invariant.
- **Permissions:** USER_UPDATE; ADMIN_ROLE_ASSIGN for ADMIN-role changes (P11 §18).
- **State machine:** loading → edit → submitting → error(409 last-admin, 403 role) → success.
- **Actions:** save; cancel.
- **Open questions:** none.

---

## 8. Archive user

- **URL:** `/admin/users/[id]/archive` (dialog, not a full page)
- **Purpose:** archive an inactive user (never the last ADMIN; never self).
- **Entry:** list/detail action.
- **Exit:** confirm → list (toast); cancel → closed.
- **Wireframe:**

      +--------------------------------+
      |  Archive Jane Doe?             |
      |  This user can no longer sign  |
      |  in. [Archive] [Cancel]        |
      +--------------------------------+

- **API:** existing user archive (status=ARCHIVED).
- **Permissions:** USER_UPDATE; blocked for last ADMIN (409 LAST_ADMIN) and self.
- **State machine:** confirm → submitting → error/success.
- **Actions:** archive; cancel.
- **Open questions:** none.

---

## 9. Roles list

- **URL:** `/admin/roles`
- **Purpose:** view roles (non-focus).
- **Entry:** sidebar.
- **Exit:** → role detail.
- **Wireframe:**

      +--------------------------------------------------+
      | Roles                                          |
      |  code      name                status          |
      |  ADMIN     Administrator        ACTIVE         |
      +--------------------------------------------------+

- **API:** existing role list.
- **Permissions:** ROLE_VIEW.
- **State machine:** loading → populated; error → retry.
- **Actions:** open; (create/edit out of focus).
- **Open questions:** none.

---

## 10. Role detail

- **URL:** `/admin/roles/[id]`
- **Purpose:** view a role and its permissions (non-focus).
- **Entry:** roles list.
- **Exit:** back.
- **Wireframe:**

      +--------------------------------------------------+
      |  ADMIN                            (read-only)    |
      |  permissions: USER_VIEW, USER_CREATE, ...        |
      +--------------------------------------------------+

- **API:** existing role read.
- **Permissions:** ROLE_VIEW.
- **State machine:** loading → detail; error → retry.
- **Actions:** back.
- **Open questions:** none.

---

## 11. Permissions catalog

- **URL:** `/admin/permissions`
- **Purpose:** view the permission catalogue (non-focus).
- **Entry:** sidebar.
- **Exit:** none.
- **Wireframe:**

      +--------------------------------------------------+
      | Permissions                                     |
      |  code            name                status     |
      |  USER_VIEW       View users          ACTIVE     |
      +--------------------------------------------------+

- **API:** existing permission list.
- **Permissions:** PERMISSION_VIEW.
- **State machine:** loading → populated; error → retry.
- **Actions:** none.
- **Open questions:** none.

---

## 12. Settings

- **URL:** `/admin/settings`
- **Purpose:** organization settings (non-focus).
- **Entry:** sidebar.
- **Exit:** save/cancel.
- **Wireframe:**

      +--------------------------------------------------+
      | Settings                                        |
      |  (organization_setting JSON, JsonConfigEditor)  |
      +--------------------------------------------------+

- **API:** existing organization_setting read/update.
- **Permissions:** ORGANIZATION_SETTING_VIEW / _UPDATE.
- **State machine:** loading → edit → submitting → error/success.
- **Actions:** save; cancel.
- **Open questions:** none.

---

## 13. Business rules

- **URL:** `/admin/business-rules`
- **Purpose:** view business rules incl. MAX_ADMINS_PER_ORG (non-focus).
- **Entry:** sidebar.
- **Exit:** save/cancel.
- **Wireframe:**

      +--------------------------------------------------+
      | Business rules                                  |
      |  MAX_ADMINS_PER_ORG   {"maxAdmins": 1}          |
      +--------------------------------------------------+

- **API:** existing business_rule read/update.
- **Permissions:** BUSINESS_RULE_VIEW / _UPDATE.
- **State machine:** loading → edit → submitting → error/success.
- **Actions:** save; cancel.
- **Open questions:** none.

---

## 14. Lookups

- **URL:** `/admin/lookups`
- **Purpose:** view lookup seed data (non-focus).
- **Entry:** sidebar.
- **Exit:** save/cancel.
- **Wireframe:** same as SUPER_ADMIN seeds — lookups.
- **API:** existing lookup read/update.
- **Permissions:** LOOKUP_VIEW / _UPDATE.
- **State machine:** loading → edit → submitting → error/success.
- **Actions:** save; cancel.
- **Open questions:** none.

---

## 15. Audit explorer

- **URL:** `/admin/audit`
- **Purpose:** view audit entries (non-focus).
- **Entry:** sidebar.
- **Exit:** none.
- **Wireframe:**

      +--------------------------------------------------+
      | Audit                                          |
      |  time        actor     action      entity       |
      |  ...                                            |
      +--------------------------------------------------+

- **API:** existing audit_log read (filters + pagination).
- **Permissions:** AUDIT_LOG_VIEW.
- **State machine:** loading → populated; error → retry.
- **Actions:** filter, paginate, open drawer.
- **Open questions:** none.

---

## 16. System health

- **URL:** `/admin/system-health`
- **Purpose:** view system health (non-focus).
- **Entry:** sidebar.
- **Exit:** none.
- **Wireframe:** health cards (postgres/redis/backend) per P11 HealthCard.
- **API:** existing system health endpoint.
- **Permissions:** SYSTEM_HEALTH_VIEW.
- **State machine:** loading → populated; error → retry.
- **Actions:** refresh.
- **Open questions:** none.

---

## 17. Profile

- **URL:** `/admin/profile`
- **Purpose:** view/edit own profile.
- **Entry:** avatar → Profile.
- **Exit:** save/back.
- **Wireframe:** same fields as SUPER_ADMIN §16.
- **API:** `/me` read + profile update.
- **Permissions:** authenticated.
- **State machine:** loading → edit → submitting → error/success.
- **Actions:** save; back.
- **Open questions:** none.

---

## 18. Change password

- **URL:** `/change-password` (shared)
- **Purpose:** voluntary rotation.
- **Entry:** avatar → Change password.
- **Exit:** success → profile; cancel → back.
- **API:** POST /api/v1/auth/change-password (§72.1).
- **Permissions:** authenticated.
- **State machine:** idle → submitting → error/success.
- **Actions:** update; cancel.
- **Open questions:** none.
