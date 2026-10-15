# ADR-0022 — P10 Communication: event-driven notification and delivery decisions

- **Status:** Accepted
- **Phase:** P10 — Communication & Notification
- **Spec:** `specs/19-P10-Communication-V1.5.md`
- **Preserves:** the frozen communication schema (three tables), the frozen notification API, and the
  business modules' ownership of their own truth.

## Context

P10 turns business events into in-app notifications and email without letting communication touch
business transactions. The frozen schema already fixes the three tables, the notification status
vocabulary, and the API surface. This ADR records the implementation decisions the specification left
open. No table or column is added.

## Decisions

1. **No email in business modules or controllers.** Commerce, Evaluation and Assessment publish an
   in-process event; Communication consumes it (`§6, §18, §188`). No business service or controller
   holds an email client.

2. **Delivery never participates in the business transaction.** The producer publishes inside its
   transaction; Communication handles it with `@TransactionalEventListener(AFTER_COMMIT)`, so a
   communication failure cannot roll back a purchase, a payment, or a result publication (`§19, §28`).
   The listener swallows and logs any failure.

3. **Durable handoff, then a worker.** The after-commit listener writes only a durable
   `background_job` (`NOTIFICATION_REQUEST`), and the worker creates notifications and queues email
   deliveries (`§16, §32, §47`). The job write from the after-commit phase uses
   `Propagation.REQUIRES_NEW`: at `AFTER_COMMIT` the original transaction has already committed, and a
   `REQUIRED` call would join it and silently discard the insert.

4. **Notification status is only UNREAD or READ.** The frozen table is deliberately minimal — no
   delivery status, event id, template id, read time or expiry. Email delivery status is represented
   by `background_job` state, structured logs, metrics and the provider response, and is not claimed
   as durable database truth (`§8, §31`).

5. **Template rendering is a pure replacement.** `{{variableName}}` placeholders are substituted from
   an explicit model built by the caller; there is no expression evaluation, method invocation, or
   scripting, and unknown placeholders are preserved rather than blanked (`§24, §26, §27`). The
   rendered body is passed through the platform HTML sanitizer (`ContentSanitizer`, `§26, §72`).

6. **Provider behind `EmailGateway`.** Only the reference `FakeEmailGateway` ships with the platform;
   it records sends and supports failure injection. Provider payloads and secrets never reach the
   domain tables, DTOs, or logs (`§29, §86, §190`).

7. **Bounded retry with backoff.** The frozen defaults are honoured: maximum 5 attempts, exponential
   backoff from 60 seconds with plus-or-minus 20 percent jitter and a 30-minute cap (`§33, §35`).
   Retryable failures (timeout, transient provider error) return the job to the queue; permanent
   failures (missing/inactive template, unknown recipient, rejected content) fail immediately
   (`§34, §59`). The due check uses the capped delay so a retry never fires early.

8. **Announcement publish is asynchronous.** Publishing validates, sets the status ACTIVE and enqueues
   an `ANNOUNCEMENT_FANOUT` job; the worker resolves the audience and inserts notifications in bounded
   batches (`§43, §46-§49`). The audience model is frozen: all active users, resolved through Identity
   at fan-out time — no targeting table is invented (`§40`). The fan-out job is identified by its
   `background_job` id, so a completed job is not fanned out twice (`§51`).

9. **Caching.** ACTIVE templates may be cached in Redis with invalidation on write; PostgreSQL remains
   the source of truth and a Redis outage degrades to a database read (`§61, §62`). Notification lists
   are deliberately not cached because read state is transactional and user-specific (`§63`).

10. **Identity stays authoritative for password reset.** Communication sends messages; it does not
    create or validate reset tokens (`§56`). Password-reset delivery wiring remains with Identity
    (GAP-112).

## Consequences

- Asynchronous announcement fan-out, retry backoff, and provider-failure isolation are verified by
  `CommunicationIntegrationTest`; the renderer and retry policy by unit tests.
- Notifications are at-least-once: event delivery is in-process with no outbox, so a crash between the
  business commit and the listener's enqueue can lose one (GAP-110), and a crash mid-fan-out can
  duplicate (GAP-111, tolerated and documented per `§51`).
- The communication frontend (notification center, announcement UI, template admin) is deferred
  (GAP-109).

## Contract gaps discovered

Recorded in `docs/known-gaps.md` (GAP-106 … GAP-115). None was silently resolved.
