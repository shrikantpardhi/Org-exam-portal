# ADR-0048 — the SMTP email gateway and Mailpit for local delivery

- **Status:** Accepted (2026-10-26)
- **Closes:** the local-mail gap — before this, no shipped provider put a message anywhere a
  developer could read it
- **Related:** ADR-0034 (password reset delivery), GAP-002
- **Migration:** none

## Context

`specs/19-P10` §29 names an SMTP client as a legitimate provider, and §810 requires provider clients
to stay below the `EmailGateway` boundary. Until now only `FakeEmailGateway` shipped: it records
messages in memory, which is correct for tests and useless for a developer who wants to see the
password-reset mail, the announcement fan-out, or a rendered template.

The password-reset flow (ADR-0034, closes GAP-002) already enqueues a `background_job`, renders the
`PASSWORD_RESET` template and hands the result to the active gateway. Everything was in place except
a provider that actually sends.

## Decision

Two additions, both deliberately small:

| Addition | What it is |
|---|---|
| `SmtpEmailGateway` | an `EmailGateway` adapter over Spring's `JavaMailSender` |
| `mailpit` compose service | a local SMTP sink with a web UI at `http://localhost:8025` |

### Two different defaults, on purpose

| Layer | Default | Why |
|---|---|---|
| Application (`application.yml`) | `FAKE` | a host-run backend and every test must never emit SMTP traffic |
| Compose backend service | `SMTP` | `SPRING_MAIL_HOST` is pinned to the `mailpit` container, so this path cannot reach a real recipient |

The compose default is `SMTP` precisely because the transport is pinned: with `host: mailpit` and no
auth, the stack has nowhere to send mail except the container. Defaulting to `FAKE` there would mean a
developer triggers a password reset and sees nothing anywhere — correct in the abstract, useless in
practice.

The application default stays `FAKE`, and `SmtpEmailGateway` is
`@ConditionalOnProperty(havingValue = "SMTP")`. That is the guard that matters: a bare `mvnw spring-boot:run`
or a test run cannot send mail, because nothing sets the provider.

### Failure classification follows §34

| Condition | Outcome | Retryable | Why |
|---|---|---|---|
| Send succeeds | `SENT` | no | — |
| `MailException` / `MessagingException` | `FAILED` | **yes** | relay down, 4xx/5xx, auth: may recover |
| missing/blank recipient | `FAILED` | **no** | an invalid address fails identically forever |

The distinction is the whole point of the frozen contract: a permanent failure mis-labelled retryable
burns the bounded budget and hides the problem for five backoffs; a transient one mis-labelled
permanent silently drops a message a retry would have delivered.

### `query()` returns empty

SMTP has no status endpoint (§38). Synthesizing one would let the retry path believe it had
information it does not have. `Optional.empty()` is the honest answer, and the caller already handles
an ambiguous outcome.

### Configuration split

Transport (`host`, `port`, auth, TLS, socket timeouts) stays in Spring's standard `spring.mail.*`
tree, because `JavaMailSender` is auto-configured from it and duplicating those keys would create two
sources of truth. Only the envelope sender lives under `app.communication.smtp.from` —
`JavaMailSender` has no notion of a default sender, and it belongs with the other communication
settings.

Socket timeouts are bounded at 5 seconds. A hung relay must not pin a worker thread indefinitely; the
worker calls this synchronously inside a transaction-scoped job.

## Consequences

- **One new dependency:** `spring-boot-starter-mail`. It is confined to the adapter — only
  `SmtpEmailGateway` imports Jakarta Mail, so §29's "provider code stays below the boundary" holds.
- **`-o` offline builds no longer resolve.** `angus-mail` is a *runtime* dependency, so
  `mvnw -o` fails until it is present in the local repository. It is now fetched; a clean machine
  needs one online build. This is worth knowing because the repository's documented build habit is
  offline.
- **Mailpit is `profiles: [core]`.** It joins postgres and redis, because the backend's default
  provider now expects it to be there. A stack brought up without `core` has no mail sink and the
  provider would fail transiently — which the retry contract handles, but it is wasted work.

## Residuals

| Residual | Note |
|---|---|
| Mailpit is a sink, not a relay | it never delivers to real recipients; that is the intent |
| No TLS to Mailpit | local only; `STARTTLS` is configurable but off by default |
| Ports published on loopback only | nothing off-host can inject mail into the dev stack |
| `SMTP-<uuid>` references are synthetic | SMTP has no provider id to echo back |