# Frozen Contracts — Quick Index

| Contract | Frozen by | One-line rule |
|---|---|---|
| Content format | specs/01 §13 | `MARKDOWN_LATEX_V1` only |
| Math delimiters | specs/08 §42 | `\(...\)` and `\[...\]` only; `$...$` is text |
| Asset shortcodes | specs/08 §44 | `[asset: <uuid>]`, resolved server-side |
| ExamEngine | specs/08 §9–§20 | No exam-name branching anywhere |
| CDUI | specs/08 §11–§18 | Capabilities, not exam names |
| Snapshot readiness | specs/14 §21–§27 | Start Exam requires READY; creates zero snapshot rows |
| Stale-write | specs/14 §43 | ETag + If-Match; 412 on mismatch |
| AnswerKeyDetail | specs/15 §5.2 | NORMAL, BONUS, DROPPED; single correctness source |
| Evaluation versioning | specs/15 §5.3 | Partial unique index on current evaluation |
| Rank / percentile | specs/15 §35–§37 | Competition ranking; published population only |
| Scoring | specs/15 §32–§34 | BigDecimal, HALF_UP, no floor |
| AccessGrant | specs/18 §3.2 | Authorization source of truth for paid content |
| Callback idempotency | specs/18 §21 | Provider reference unique; duplicate callback is a no-op |
| Refund / revocation | specs/18 §20 | Full refund; grant to INACTIVE at refund time |
| Grant merge | specs/18 §19 | Extend existing active grant |
| Coupon | specs/18 §15 | Fixed amount only |
| Communication events | specs/19 §20 | In-process, at-least-once |
| background_job | specs/16 §58–§60 | General-purpose; conditional claim |
| Audit atomicity | specs/20 §99 | Audit insert rolls back the mutation |
| System actor | specs/20 §18 | Well-known UUID; login blocked |
| Support scope | specs/20 §35 | Granular permissions, masking by default |
| Privileged role change | specs/20 §18 | Self-block, last-admin block |
| Global search | specs/20 §39 | Fixed resource allow-list |
| Import targets | specs/20 §51 | Four targets only |
| Bulk operations | specs/20 §43 | Fixed operation allow-list |
| Report catalog | specs/22 §14–§30 | Read-only, `background_job`-backed, protected artifacts |

**If a task touches a row in this table, load the corresponding spec section before planning.**