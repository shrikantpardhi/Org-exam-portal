# P10 — Communication & Notification: Testing Notes

Phase specification: `specs/19-P10-Communication-V1.5.md`.

## Scope verified

- Notification, EmailTemplate and Announcement frozen tables and constraints.
- The notification center: list, filter, read, read-all, unread count, and self-scoping.
- Asynchronous announcement fan-out through `background_job`.
- Bounded email retry with exponential backoff and jitter.
- Business-event handlers for Commerce, Evaluation and Assessment.
- Provider-failure isolation from the business transaction.

## Unit tests

| Test | What it proves |
|---|---|
| `communication/service/RetryPolicyTest` | backoff grows, is capped at 30 minutes, jitter stays within ±20% |
| `communication/service/TemplateRendererTest` | pure `{{variable}}` replacement; no expression evaluation; unknown placeholders preserved |

Run: `cd backend && ./mvnw test -Dtest="RetryPolicyTest,TemplateRendererTest"`.

## Integration tests

| Test | What it proves |
|---|---|
| `CommunicationMigrationIntegrationTest` | three tables; notification status restricted to UNREAD/READ; the minimal notification shape; constraints and indexes; template catalog and permission seeds |
| `CommunicationIntegrationTest.notificationCenterIsSelfScopedAndReadIsIdempotent` | list/filter, read idempotency (UNREAD→READ, READ→READ), read-all scoped to the caller, IDOR returns 404 |
| `CommunicationIntegrationTest.announcementPublishQueuesAsynchronousFanOut` | **the asynchronous fan-out verification**: publish returns before any recipient is written; the worker fans out; a retry does not fan out twice; the student sees the published announcement |
| `CommunicationIntegrationTest.emailRetriesWithBackoffThenSucceeds` | **the retry-backoff verification**: a transient failure returns the job to QUEUED with attempt count 1; the job is not due before its backoff; after the window it succeeds and the email contains the rendered variable |
| `CommunicationIntegrationTest.permanentlyInvalidDeliveryFailsWithoutRetry` | an unknown template fails permanently (no retry loop) |
| `CommunicationIntegrationTest.providerFailureDoesNotRollBackTheBusinessTransaction` | **the provider-failure-isolation verification**: the payment callback commits SUCCESS and creates a durable notification request after commit; running the workers against a down provider creates the in-app notification but sends no email, and never changes the business state |

Run: `cd backend && ./mvnw test -Dtest="*Communication*"`.

## Manual/curl smoke

```bash
# Notification center (student, self-scoped)
curl -H "Authorization: Bearer <student>" $BASE/api/v1/notifications
curl -X POST -H "Authorization: Bearer <student>" $BASE/api/v1/notifications/<id>/read
curl -X POST -H "Authorization: Bearer <student>" $BASE/api/v1/notifications/read-all

# Announcement publish (staff/admin) — returns a fan-out job id, not synchronous delivery
curl -X POST -H "Authorization: Bearer <admin>" -H 'Content-Type: application/json' \
     -d '{"title":"Maintenance","description":"Scheduled maintenance."}' $BASE/api/v1/announcements
curl -X POST -H "Authorization: Bearer <admin>" $BASE/api/v1/announcements/<id>/publish
curl -H "Authorization: Bearer <student>" $BASE/api/v1/announcements

# Template admin (admin)
curl -H "Authorization: Bearer <admin>" $BASE/api/v1/email-templates
```

## Not exercised / deferred

- A real email provider adapter (only the reference `FAKE` provider ships) — GAP-108.
- The communication frontend (notification center, announcement UI, template admin) — GAP-109.
- A durable email delivery table (delivery status is `background_job` + logs + metrics) — GAP-107.
- An outbox for exactly-once event handoff; notification duplication is tolerated per §51 — GAP-110/111.
- Rate limiting on notification/announcement APIs — GAP-115.
