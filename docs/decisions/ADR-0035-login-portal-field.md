# ADR-0035 — Optional `portal` field on login (split login portals)

- **Status:** Accepted
- **Phase:** SUPER_ADMIN / ADMIN UI + split login (amendment)
- **Spec:** `specs/05-API-Contract-V1.5.md` §21.1; `specs/10-P1-Identity-V1.5.md` §95–§96
- **Closes:** — (new portal-separation contract; resolves conflict C7)
- **Migration:** none (no schema change)

## Context

The owner requires three separate login portals — System Admin (SUPER_ADMIN), Organization
Admin (ADMIN), and Student (STUDENT) — that authenticate against the same
`POST /api/v1/auth/login` endpoint. The frozen contract has no `portal` field, so the backend
cannot authoritatively validate that an account belongs to the portal it is signing into.

## Decision

Add an optional `portal` field to `POST /api/v1/auth/login`:

```json
{ "username": "…", "password": "…", "portal": "SYSTEM_ADMIN" }
```

- `portal` is an optional enum: `SYSTEM_ADMIN` | `ORGANIZATION_ADMIN` | `STUDENT`.
- When present, the backend validates that the authenticated account's roles match the portal:
  - `SYSTEM_ADMIN` → the user holds `SUPER_ADMIN`.
  - `ORGANIZATION_ADMIN` → the user holds `ADMIN`.
  - `STUDENT` → the user holds `STUDENT`.
- On mismatch the backend returns `401 PORTAL_MISMATCH` with a single generic message
  ("this account cannot use this portal") and no detail about which portal the account does
  belong to, so a wrong portal reveals nothing.
- When `portal` is absent, behaviour is unchanged (full backwards compatibility).

No new endpoint and no schema change. The backend remains the authorization boundary for
portal membership.

## Rejected alternatives

- **Frontend-only portal separation.** The frontend renders the portal and redirects the user by
  role after login. Rejected: the client becomes the authority for role→portal mapping, and a
  mismatched account still receives a token before being redirected — a weaker boundary ("never
  weaken authentication").
- **Three separate login endpoints.** Fragments the frozen contract and duplicates the login flow
  for no gain; one endpoint plus an optional field is a smaller, additive change.

## Consequences

- The three portals share one login endpoint and each passes its own `portal` value.
- Portal membership is validated server-side; `PORTAL_MISMATCH` is generic and non-revealing.
- `GET /me` (and the login response) already carry the account's roles and `mustChangePassword`,
  so the shell can still route the user after a successful login.

## Rollback

Remove the `portal` field from the request DTO and the role check; the frontend stops sending it.
Login reverts to the pre-field behaviour with no schema impact.
