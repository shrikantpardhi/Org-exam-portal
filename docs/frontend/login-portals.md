# Split Login Portals — Design

Three separate portals authenticate against the same `POST /api/v1/auth/login` endpoint and
validate the account's role against the chosen portal (ADR-0035).

| Portal | Route | Role checked | Post-login home |
|---|---|---|---|
| System Admin | `/login/admin` | SUPER_ADMIN | `/platform` |
| Organization Admin | `/login/organization` | ADMIN | `/admin` |
| Student | `/login/student` | STUDENT | `/student` |

`/login` (legacy) redirects to `/login/student` — the most common portal — for backwards
compatibility.

## Shared structure (each portal)

- **Title / subtitle:** portal-specific, e.g. "System admin sign in" / "For the platform's
  installation owner."
- **Layout:** public layout; a centred card (email, password, "Sign in", "Forgot password?")
  with the portal name shown clearly. A small "wrong portal?" hint links to the other two
  portals without implying where the account belongs.
- **API call:** `POST /api/v1/auth/login` with `{ username, password, portal }` where
  `portal` is `SYSTEM_ADMIN` | `ORGANIZATION_ADMIN` | `STUDENT` respectively.
- **On success:** store the token, refetch `GET /me`, then redirect per the table above; a
  safe `next` query wins.
- **must_change_password:** when the login response (or `GET /me`) reports
  `mustChangePassword: true`, redirect to `/change-password` before the role home.

## Error handling

| Condition | Shown | Behavior |
|---|---|---|
| Invalid credentials | "Email or password is incorrect." | stay, clear password |
| `401 PORTAL_MISMATCH` | "This account cannot use this portal." | generic — never names the correct portal |
| `must_change_password` | n/a | redirect to `/change-password` |
| Rate-limited / 429 | "Too many attempts. Try again shortly." | stay |

`PORTAL_MISMATCH` is deliberately generic: the message is identical for every portal and
carries no hint of the account's actual portal, so a wrong-portal attempt reveals nothing.

## Forgot password

Each portal links to the shared `/forgot-password` page (existing flow). The reset link email
is the same for all roles; after reset the user signs in to their own portal.

## Link from the landing page

The landing footer/nav "Sign in" opens `/login/student`; the login card's "wrong portal?"
hint links to `/login/admin` and `/login/organization`. The landing page does not
pre-select a portal.

## Redirect after login

| Role | Home |
|---|---|
| SUPER_ADMIN | `/platform` |
| ADMIN | `/admin` |
| STUDENT | `/student` |
| any role with `mustChangePassword` | `/change-password` (overrides the above) |

## Open questions

- None. Portal enforcement is backend-authoritative (ADR-0035); the client only chooses the
  URL and sends the matching `portal` value.
