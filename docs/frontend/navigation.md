# Navigation, Shell, and Public Layout — Landing / Login / SUPER_ADMIN / ADMIN

Two layouts exist: a **public layout** (landing, login portals, `/docs`) with no sidebar, and
the **authenticated shell** shared by the SUPER_ADMIN (`/platform/*`) and ADMIN (`/admin/*`) route trees. One shell, two sidebar menus, swapped by the signed-in role.

## Public layout

For `/`, `/login/*`, `/forgot-password`, `/reset-password`, and `/docs/*`:

- **Header:** logo · nav · "Sign in" — transparent over the landing hero,
  solid on scroll. No sidebar, no org name, no search, no bell, no avatar.
- **Landing** renders the marketing sections (see public-landing.md).
- **Login portals** render a centred card (see login-portals.md).
- **Docs** render the docs layout — header, docs sidebar, content, right TOC (see docs-site.md).
- **Footer:** links to `/docs`, `/docs/terms`, `/docs/privacy`, `/docs/support`, and `/login`.

## Shell layout

Exact dimensions:

- **Sidebar:** 264 px expanded; **56 px** icon-only collapsed. Full height, fixed left.
- **Header:** 64 px tall, fixed top, spans the remaining width (right of the sidebar).
- **Content:** scrollable region below the header, right of the sidebar; top padding 24 px,
  horizontal padding 24 px (16 px below 768 px).
- **Sidebar drawer (mobile ≤ 768 px):** the sidebar hides and becomes a slide-in drawer,
  264 px wide, with a scrim overlay; it closes on scrim tap, Escape, or route change.

Shell structure:

    +-----------+--------------------------------------------+
    |           | Header (64px): toggle · logo · org · search |
    | Sidebar   |              · bell · theme · avatar       |
    | 264px     +--------------------------------------------+
    | (56px     |                                            |
    |  icon)    |  Content (breadcrumbs + page)              |
    |           |                                            |
    +-----------+--------------------------------------------+

## Header

Elements, left to right:

1. **Sidebar toggle** (left edge) — collapses/expands the sidebar on desktop; opens the drawer on mobile.
2. **Logo** — the platform mark; clicking it navigates to the role home (/platform or /admin).
3. **Organization name** — the signed-in user's organization `name`; "Platform" when the user is SUPER_ADMIN.
4. **Global search** — a placeholder input; focus opens the search modal. Search is a modal because backend global search is a separate P11 capability; the modal is the only entry point. Out of the primary focus for this task; the modal lists "coming in this task" for resources not yet wired.
5. **Notification bell** — reuses the P10 notification popover (`components/communication/notification-popover.tsx`); shows the unread badge.
6. **No theme toggle** — the application ships the single validated light theme. `specs/Design-Theme.md`
   §21 permits only a mode that has been validated, and only one mode is validated, so only one is
   offered (ADR-0050).
7. **Avatar menu** — the user's initials; expands to Profile, Change password, and Sign out.

## Sidebar

Sections are permission-gated: a section renders only when the signed-in user holds the
listed permission. "Hidden menu items are not authorization" (P11 §63) — the backend is
still authoritative; the gate is UX only.

### SUPER_ADMIN sidebar (`/platform`)

| Section | Permission | Link |
|---|---|---|
| Dashboard | (authenticated) | /platform |
| Organizations | PLATFORM_ORGANIZATION_VIEW | /platform/organizations |
| Providers | PLATFORM_PROVIDER_VIEW | /platform/providers |
| System info | PLATFORM_SYSTEM_VIEW | /platform/system |
| Seeds: roles | PLATFORM_SYSTEM_VIEW | /platform/seeds/roles |
| Seeds: permissions | PLATFORM_SYSTEM_VIEW | /platform/seeds/permissions |
| Seeds: lookups | PLATFORM_SYSTEM_VIEW | /platform/seeds/lookups |
| Multi-org (disabled) | PLATFORM_ORGANIZATION_VIEW | /platform/multi-org |

### ADMIN sidebar (`/admin`)

User management is the priority; the rest remain reachable but not the focus.

| Section | Permission | Link |
|---|---|---|
| Dashboard | (authenticated) | /admin |
| Users | USER_VIEW | /admin/users |
| Roles | ROLE_VIEW | /admin/roles |
| Permissions | PERMISSION_VIEW | /admin/permissions |
| Settings | ORGANIZATION_SETTING_VIEW | /admin/settings |
| Business rules | BUSINESS_RULE_VIEW | /admin/business-rules |
| Lookups | LOOKUP_VIEW | /admin/lookups |
| Audit | AUDIT_LOG_VIEW | /admin/audit |

Collapsed state (icon-only): each item shows its icon with a tooltip; section labels are
hidden. Expanded state shows icon + label.

## Auth gate

Three route classes:

1. **Public** — `/` (landing), `/login/admin`, `/login/organization`, `/login/student`,
   `/forgot-password`, `/reset-password`, `/docs/*`. No shell, no token required.
2. **Authenticated** — `/change-password`, `/platform/*`, `/admin/*`. Requires a valid token.
   Without one, redirect to `/login` and preserve the intended URL as a `next` query so the
   post-login redirect can restore it.
3. **Role-required** — every `/platform/*` route requires the SUPER_ADMIN role; every
   `/admin/*` route requires the ADMIN role. A user with the wrong role is redirected to
   their own role home (SUPER_ADMIN → /platform, ADMIN → /admin, others → existing role
   landing) with a single warning toast.

Gate order (client): token → mustChangePassword → role → permission (per screen).

## must_change_password gate

Shell-level redirect, before any route renders:

- When the signed-in user's `mustChangePassword` is `true`, every route except
  `/change-password` redirects to `/change-password`.
- The gate reads the flag from the auth context (populated by `GET /me`), not from the JWT.
- Once the password is changed (POST /auth/change-password clears the flag), the auth
  context refetches and the redirect lifts automatically.

## Redirect after login

| Signed-in role | Destination |
|---|---|
| SUPER_ADMIN | /platform |
| ADMIN | /admin |
| STUDENT | /student |
| STAFF / REVIEWER / SUPPORT / FINANCE | existing role landing (unchanged) |
| any role with mustChangePassword=true | /change-password (overrides the above) |

A `next` query parameter, when present and safe (same-origin, relative), takes precedence
over the role default.

## Theme

Per `specs/Design-Theme.md` §21: **no dark mode until validated; only the validated mode is offered.**

- The application ships a single **light** theme. There is no toggle and no `dark` class.
- All colour comes from semantic tokens defined once in `app/globals.css` and mapped in
  `tailwind.config.ts` (`surface`, `border`, `muted`, `primary`, `success`, `warning`,
  `destructive`, `info`). Components reference those tokens, never a palette literal.
- `color-scheme: light` is declared so native controls match the shipped theme.
- Adding a dark theme later means validating it first and re-introducing the toggle then; it is a
  deliberate future change, not an unvalidated feature shipped by accident (ADR-0050).

## Mobile behavior

- ≤ 768 px: sidebar becomes a drawer (264 px) over a scrim; header keeps logo, search
  (icon), bell, avatar.
- Tables degrade to stacked cards or horizontal scroll; the admin DataTable renders a
  horizontal-scroll wrapper rather than dropping columns silently.

## Breadcrumbs

- Rendered above the page title inside the content region.
- Composed from the current route: home segment (Platform / Admin) → resource → detail.
- Detail crumbs show the resource's human name when loaded (e.g., "Organizations / Acme"),
  and fall back to the id while loading.
- The last crumb is the current page and is not a link (aria-current="page").
