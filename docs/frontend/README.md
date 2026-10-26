# Frontend Design — Landing, Split Login, SUPER_ADMIN and ADMIN UI

This folder is the design authority for the public landing page, the three split login
portals, the in-product `/docs` site, and the SUPER_ADMIN and ADMIN user interfaces built
in the SUPER_ADMIN / ADMIN UI task (ADR-0032, ADR-0033, ADR-0034, ADR-0035, ADR-0036,
ADR-0037). It documents the shell, navigation, route maps, and the screen-by-screen state
machines before any component code is written.

## Personas (this task)

| Role | One line |
|---|---|
| SUPER_ADMIN | Installation owner; creates the first business organization and its ADMIN, manages platform-level provider config, system info and seeds. |
| ADMIN | Business-organization administrator; manages users and (non-focus) roles, permissions, settings, rules, lookups, audit and health. |
| STUDENT | Self-service exam taker. Out of scope here. |
| STAFF | Operational staff. Out of scope here. |
| REVIEWER | Content reviewer. Out of scope here. |
| SUPPORT | Support operator. Out of scope here. |
| FINANCE | Commerce/finance operator. Out of scope here. |

## Route maps

### Public (landing, login portals, docs)

See [public-landing.md](public-landing.md), [login-portals.md](login-portals.md),
[docs-site.md](docs-site.md).

    /                          public landing page (marketing)
    /login/student             student login portal
    /login/organization        organization admin (ADMIN) login portal
    /login/admin               system admin (SUPER_ADMIN) login portal
    /forgot-password           shared (all roles)
    /reset-password            shared invitation reset (link from email)
    /change-password           shared forced first-login password change
    /docs                      documentation site (public)
    /docs/{page}               docs page (getting-started, roles, api, configuration, architecture, terms, privacy, support)

### SUPER_ADMIN (`/platform/*`)

See [super-admin-flow.md](super-admin-flow.md) for the full screen spec.

    /login/admin                           SUPER_ADMIN portal (portal: SYSTEM_ADMIN)
    /change-password                       shared forced first-login password change
    /platform                              platform dashboard (empty or with org)
    /platform/organizations                organizations list
    /platform/organizations/new            create organization
    /platform/organizations/[id]           organization detail (no admin / with admin)
    /platform/organizations/[id]/admins/new  create ADMIN for the organization
    /platform/providers                    provider configuration (reference-adapters banner)
    /platform/system                       system information (container note)
    /platform/seeds/roles                  seeds: roles
    /platform/seeds/permissions            seeds: permissions
    /platform/seeds/lookups                seeds: lookups
    /platform/multi-org                    multi-org placeholder (disabled)
    /platform/profile                      SUPER_ADMIN profile
    /platform/change-password              SUPER_ADMIN change password (reuses shared page)

### ADMIN (`/admin/*`)

See [admin-flow.md](admin-flow.md) for the full screen spec.

    /login/organization                    ADMIN portal (portal: ORGANIZATION_ADMIN)
    /reset-password                        shared invitation reset (link from email)
    /admin                                 admin dashboard
    /admin/users                           users list
    /admin/users/new                       create user
    /admin/users/[id]                      user detail
    /admin/users/[id]/edit                 edit user
    /admin/users/[id]/archive              archive user (dialog)
    /admin/roles                           roles list
    /admin/roles/[id]                      role detail
    /admin/permissions                     permissions catalog
    /admin/settings                        settings
    /admin/business-rules                  business rules
    /admin/lookups                         lookups
    /admin/audit                           audit explorer
    /admin/system-health                   system health
    /admin/profile                         admin profile
    /change-password                       change password (reuses shared page)

## Documents

- [public-landing.md](public-landing.md) — landing page sections, copy, CTAs, contact form.
- [login-portals.md](login-portals.md) — the three split login portals and portal validation.
- [navigation.md](navigation.md) — shell + public layout, header, sidebar, auth gates, redirects, theme, mobile, breadcrumbs.
- [super-admin-flow.md](super-admin-flow.md) — every SUPER_ADMIN screen.
- [admin-flow.md](admin-flow.md) — every ADMIN screen.
- [user-creation-flow.md](user-creation-flow.md) — SUPER_ADMIN-creates-ADMIN vs ADMIN-creates-non-ADMIN, side by side.
- [config-surface.md](config-surface.md) — SUPER_ADMIN + ADMIN config/system-info surfaces.
- [docs-site.md](docs-site.md) — the `/docs` site layout, pages, search, version indicator.

## Out of scope

The STUDENT **login portal** (`/login/student`) is included, but STUDENT, STAFF,
REVIEWER, SUPPORT and FINANCE **flows** are **not** part of this task. Their routes are
unchanged and remain outside the shell described here. Only the landing page, the three
login portals, `/docs`, and the SUPER_ADMIN and ADMIN route trees are reworked.

## Frozen specs governing behavior

- `specs/10-P1-Identity-V1.5.md` — roles, permissions, auth, password change (§9, §18, §56, §58).
- `specs/20-P11-Administration-V1.5.md` — admin navigation (§63), tables (§65), components (§123), query/form/loading/empty/error states (§124–§128), accessibility (§129), responsive behavior (§130).
- `specs/22-P13-Reporting-V1.5.md` §75 — UPPER_SNAKE permission naming convention.
- `specs/Design-Theme.md` — token-only colors, first-class dark mode, WCAG 2.1 AA.
- ADR-0032/0033/0034 — SUPER_ADMIN role, PLATFORM org, first-password delivery and `must_change_password`.
- ADR-0035/0036/0037 — login `portal` field, public `/docs` section, org-config + public contact endpoints.

Any screen behavior that conflicts with a frozen spec is a stop-and-report condition, not a
silent deviation.
