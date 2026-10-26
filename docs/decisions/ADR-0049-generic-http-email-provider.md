# ADR-0049 — a generic JSON-over-HTTP email provider adapter

- **Status:** Accepted (2026-10-26)
- **Closes:** GAP-108 (no real email provider adapter shipped)
- **Related:** ADR-0048 (SMTP gateway and Mailpit)
- **Migration:** none

## Context

ADR-0048 added SMTP, which covers self-hosted relays, Mailpit, and the many hosted providers that
expose an SMTP endpoint (SendGrid, Mailgun, Postmark, Brevo, SES SMTP). It does not cover providers
whose *only* integration is a JSON HTTP API, and it forces the operator to maintain SMTP credentials
they may not have.

`specs/19-P10` §29 does not freeze a provider. Writing one class per vendor would encode a product
decision the specification explicitly leaves open, and would mean a code change — a new adapter, a
new build, a new review — every time the operator changes vendor. The user's requirement is that
switching provider is a configuration change.

## Decision

One adapter, `HttpApiEmailGateway`, that speaks the shape almost every transactional provider shares:
**POST a JSON body to a URL with the API key in a header.**

| Setting | What it controls |
|---|---|
| `url` | the endpoint |
| `auth-scheme` / `auth-header` | `BEARER`, `X_API_KEY`, `API_KEY` or `NONE` |
| `api-key-env` | the **name** of the environment variable holding the key |
| `body-template` | the JSON payload, with `{to}` `{from}` `{subject}` `{html}` `{text}` placeholders |
| `success-field` / `success-value` | a JSON field that must indicate success |
| `reference-field` | where the provider's message id lives |
| `error-field` | where an error message lives, for diagnostics |
| `connect-timeout-ms` / `request-timeout-ms` | bounds so a hung provider cannot pin a worker |

Adopting a provider is then entirely configuration. SendGrid, for example:

```yaml
app:
  communication:
    provider: HTTP_API
    http-api:
      url: https://api.sendgrid.com/v3/mail/send
      api-key-env: SENDGRID_API_KEY
      body-template: '{"personalizations":[{"to":[{"email":{to}}]}],"from":{"email":{from}},"subject":{subject},"content":[{"type":"text/html","value":{html}}]}'
```

### The key is never configuration

Configuration carries the *name* of an environment variable, never the key. The value is resolved
from the `Environment` at send time, so a key cannot be serialized into an actuator response, echoed
into an error payload, or committed. This also satisfies the standing rule against committing secrets
and means the compose file forwards `EMAIL_API_KEY` from the host without ever naming its value.

### Failure classification stays inside §34

| Response | Outcome | Retryable |
|---|---|---|
| 2xx | `SENT` | no |
| 2xx but `success-field` says otherwise | `FAILED` | no |
| 408, 429, 5xx | `FAILED` | **yes** |
| other 4xx | `FAILED` | **no** |
| transport error / timeout | `TIMEOUT` | **yes** |
| missing key, no URL, bad template | `FAILED` | **no** |

The 2xx-with-error-envelope case is the one worth calling out: several providers answer `200` with
`{"status":"error"}`. Trusting the status alone would record a dropped message as delivered.

### It refuses to start half-configured

A missing `url` or `body-template` throws at construction. The alternative — starting anyway and
discovering it through five failed retries — spends the bounded budget and hides an operator mistake.

## Consequences

- **Switching vendor is configuration.** No Java, no rebuild of logic, no new adapter per vendor.
- **`api-key-env` is required in practice.** Without it the adapter fails permanently on first send,
  by design: retrying will not create a credential.
- **`query()` returns empty.** A generic adapter cannot assume a status endpoint exists (§38).
- **The adapter is disabled unless `provider: HTTP_API`.** Neither tests nor a host-run backend
  can emit traffic by accident; the `FAKE` default is untouched.
- **One gap partially remains.** Providers that sign requests with HMAC (rather than a static key in
  a header) are out of scope; that needs an adapter of its own. Recorded as GAP-188.

## Verification

Against the running stack, not a mock: a stub provider was attached to the compose network and the
backend was pointed at it with configuration only. A real `POST /auth/forgot-password` produced:

| Observed | Value |
|---|---|
| method / path | `POST /send` |
| auth header | `Bearer live-stub-key-abc123` |
| body `to` | `demo.student@example.test` |
| body `from` | `no-reply@exam-platform.local` |
| body `subject` | `Reset your password` |
| body `html` | 581 chars, contains `reset-password?token=` |

13 unit tests cover the wire format against a real `HttpServer`, the auth schemes, the retry split,
the 2xx-error case, JSON escaping, and the fail-fast configuration checks.