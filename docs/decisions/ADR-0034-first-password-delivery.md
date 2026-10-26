# ADR-0034 — First-password delivery by email and the must_change_password flag

- **Status:** Accepted
- **Phase:** SUPER_ADMIN / ADMIN UI (amendment raised by the owner's "first password by email, change on first login" requirement)
- **Spec:** `specs/10-P1-Identity-V1.5.md` §24–§28 (password reset), §56, §58
- **Closes:** GAP-002
- **Migration:** `V30__must_change_password.sql`

## Context

GAP-002 records that the password-reset contract is complete but has **no delivery
channel**: `PasswordResetDelivery` is an Identity interface with no email
implementation, so a reset token can be minted but never received. The owner requires
that any user's first password is delivered by email and changed on first login.

## Decision

Close GAP-002 by wiring the existing reset-link flow to a real transport:

1. `Identity.PasswordResetDelivery` gains a Communication-backed implementation that
   hands the reset token to `Communication.EmailGateway` (P10), reusing the existing
   reset-link email path. The send is handed off as a `background_job` to
   `EmailDeliveryWorker`.
2. First passwords are **never shown or sent**; the invitation path sends a reset
   link (the owner's decision: "send the link, not the password").
3. `V30` adds `user.must_change_password BOOLEAN NOT NULL DEFAULT FALSE`. The
   bootstrap CLI and the ADMIN/SUPER_ADMIN create-user path set it `TRUE`; the auth
   filter then allows only `POST /auth/change-password`, `POST /auth/logout` and
   `GET /me` until the flag is cleared.

## Rejected alternative

- **Show the password once on screen.** Rejected per the owner's decision: a first
  password that is shown once is still a shared secret in a browser tab, and it cannot
  force the "change on first login" contract. The reset-link flow already exists, is
  audited, and expires, so it is the correct transport.

## Consequences

- GAP-002 closes: reset links (and therefore first passwords) now reach real mailboxes.
- The auth filter's allowed-path set is extended with a `must_change_password` gate;
  this is a security boundary change and is covered by `MustChangePasswordIntegrationTest`.
- The existing `PASSWORD_RESET` email template is reused as the transport for both
  self-service resets and first-password invitations, with a `{{resetLink}}` placeholder
  added by `V31`; no separate invitation template is introduced.

## Rollback

`DROP` the `must_change_password` column (`V30`); the flag becomes a no-op in the
auth filter once removed. Delivery wiring rolls back independently by removing the
Communication-backed `PasswordResetDelivery` bean, which reopens GAP-002.
