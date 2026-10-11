# Known Gaps and Deferred Items

- **Version:** 1.5
- **Updated:** 2026-10-10
- **Applies to:** P0 (Foundation) through P5 (Assessment Delivery and Runtime Examination Engine)

## Purpose

`AGENTS.md` rule 20 requires that a frozen-contract gap is **stopped, reported, and not silently
resolved**. This register is the single durable place where those gaps and deliberate deferrals
are recorded, so they are visible to review, QA, and the next phase rather than buried in chat or
in a passing comment.

A gap here is not automatically a defect. Each entry states whether it is an **open gap**, a
**deferred item with a target phase**, a **by-design limitation**, or an **information note**. No
entry authorises a change to a frozen contract; a change that would do so must go through the
architecture-change process (`specs/09-P0-Foundation-V1.5.md §86`).

## 1. Gap register

| ID | Area | Gap | Impact | Reason / frozen constraint | Target | Status |
|---|---|---|---|---|---|---|
| GAP-001 | Identity audit | Immutable `audit_log` persistence is not written by P1; only structured `SECURITY` log events are emitted | No durable, queryable audit trail for login/logout/reset/role events until Administration lands | `audit_log` is owned by Administration (frozen migration `V11`); P1 must not invent a second audit table (`10-P1 §48, §196`) | P11 Administration | Deferred (ADR-0013) |
| GAP-002 | Password reset | No delivery channel (email/SMS) for reset tokens | End users cannot receive a reset link through the product yet; the backend reset contract is complete | Email/notification platform is out of P1 scope (`10-P1 §184`) | P10 Communication | Deferred (ADR-0012) |
| GAP-003 | Authentication | Refresh is bound to the current active session and cannot revive an expired access token | A user whose access token expired must log in again rather than silently refresh | No `refresh_token`/`token_hash`/`expires_on` column exists and `§42` forbids adding one | Revisit if a true refresh token is required (schema change + ADR) | By-design limitation (ADR-0011) |
| GAP-004 | Sessions | No idle/inactivity timeout; only an absolute lifetime | A session stays valid until its absolute lifetime even if unused | `login_session` has no last-activity column (`10-P1 §41, §42`) | Revisit with a schema change or ADR | By-design limitation (ADR-0011) |
| GAP-005 | Frontend token storage | Access token in `sessionStorage` is readable by page scripts | XSS on the origin can exfiltrate the token; mitigated by short TTL + immediate session revocation but not eliminated | Source does not prescribe storage; P1 chose one documented strategy (`10-P1 §99`; ADR-0010) | Hardening phase (httpOnly cookie requires an API transport change) | Open (accepted for P1) |
| GAP-006 | Sessions API scope | Spec self-contradiction: the authorization matrix (`§68`) lists `SESSION_VIEW` for login-session list/get, while `§44` requires a student to see only their own sessions | A reviewer must confirm the intended model | Two frozen sections disagree | Architecture confirmation | Resolved in favour of `§44` (self-scope; `SESSION_VIEW` widens); recorded in the acceptance doc |
| GAP-007 | Infrastructure | Docker Compose stack absent; container runtime, `nginx -t`, and PgBouncer path not runnable | P0 exit gate (P0-09, P0-10, P0-14) cannot be signed off | No Docker daemon on the development host | Next infrastructure task | Open |
| GAP-008 | CI/CD | No CI workflow exists; the acceptance criterion "CI passes" is unverifiable; tests pass only locally | Regressions are not caught automatically; no required status check on `main` | P0-12 not yet implemented | P0-12 CI/CD | Open |
| GAP-009 | Observability | Prometheus/Grafana/Loki configuration files absent; only actuator endpoints and structured logging exist | Metrics/log aggregation pipeline not reproducible | P0-11 partially implemented | P0-11 / hardening | Open |
| GAP-010 | Security process | Secret scanning and dependency checks are not wired into any pipeline | A committed secret would not be caught automatically | P0-13 partially implemented; no CI | P0-13 with CI | Open |
| GAP-011 | Authentication hardening | Rate limiting and brute-force throttling for `login`, `forgot-password`, and `reset-password` are not implemented, though the `429 RATE_LIMITED` category exists | Unlimited password-guessing is possible beyond the cost of hashing | `§95, §96, §140`: exact algorithm/thresholds require implementation and security review and are environment configuration | Hardening / security review follow-up | Open |
| GAP-012 | Authentication | JWT signing-key rotation is not implemented | Key rotation is a manual/ops procedure | `§147`: a full rotation system is not invented in P1 | Operations hardening | Deferred |
| GAP-013 | Authentication | Login timing side-channel is reduced (dummy hash work on unknown accounts) but not eliminated | A sophisticated observer may still distinguish accounts by timing | Constant-time login is not fully achievable without a fixed-cost verifier | Hardening | Open (low) |
| GAP-014 | Authentication | The filter accepts `sid`-less tokens (identity only, no permissions) to keep the P0 smoke tokens valid | Not reachable in production: only a holder of the signing secret can mint such a token, and it grants no permission | P0 boundary compatibility (`09-P0 §31`; ADR-0011) | Revisit when P0 smoke tokens are migrated to real sessions | Information note |
| GAP-015 | Edge security | HTTPS enforcement, security headers, frame/clickjacking policy, and CORS preflight behaviour are unverified because the edge is not built | Edge-level protections are not evidenced | Depends on GAP-007 (Nginx) | With P0-10 / hardening | Open |
| GAP-016 | Migrations | Identity is delivered as `V2` (tables) and `V3` (seeds) rather than the schema document's *recommended* `V2`/`V13`/`V107` grouping | Numbering differs from the recommended list | The schema document states the grouping is "Recommended" and "actual migration numbering may differ" | None (informational) | Resolved / informational |
| GAP-017 | Schema integrity | `login_session` columns `user_id`, `login_time`, `session_status` are `NOT NULL`, which the schema section did not enumerate | Stricter than the literal table listing | Integrity of the security boundary; `§` says the database is the final boundary | None (informational) | Resolved / informational |
| GAP-018 | Schema integrity | `CHECK` constraints on `status`/`session_status` columns | Stricter than the literal table listing | Sanctioned by the enum strategy: "controlled VARCHAR values with check constraints … do not allow arbitrary free-text status values" (`04-Schema §10`) | None (informational) | Resolved / informational |
| GAP-019 | Environment | The development host sets `NODE_ENV=production`, which makes `npm ci` skip devDependencies and hangs `next build` with a non-standard value | Local verification must clear `NODE_ENV`; `vitest.config.ts` already forces the test environment | Host configuration, not code | Documented runbook step | Open (environmental) |
| GAP-020 | Migrations | Academic tables are `V4` and permission seeds `V5`, rather than the schema document's recommended `V3`/`V13` grouping | Numbering differs from the recommended list | The document says the grouping is "Recommended" and "actual migration numbering may differ"; P1 already used `V2`/`V3` | None (informational) | Resolved / informational (ADR-0015) |
| GAP-021 | Authorization | Academic permission codes (`EXAM_VIEW` … `TAG_ARCHIVE`) are seeded as implementation candidates from `11-P2 §55`; the API contract names none | If the Identity catalog later uses different codes, seeds and `@PreAuthorize` strings must be realigned | The permission entity is configuration-driven master data; the contract omits academic codes | Realign with Identity role/permission configuration | Open |
| GAP-022 | Mapping | A single `AcademicMapper` is used instead of the spec's seven named mapper classes | Reviewers expecting one class per resource will not find them | The spec lists names but does not freeze the count; entity/DTO boundary is still explicit | None (informational) | Resolved / informational (ADR-0015) |
| GAP-023 | Caching | No Redis cache is added for Academic masters in this slice | Stable masters (languages, tags, exam summaries) are read from PostgreSQL every time | `11-P2 §70` permits *selective* caching; correctness-first and queries are already index-backed | Optional hardening when load data justifies it | Deferred (ADR-0015) |
| GAP-024 | Academic seed data | No Language or Tag reference catalog is seeded | A pilot must create languages and tags before they can be used; there is no default vocabulary | `11-P2 §83/§84` make seeding optional and pilot-driven; `04-Schema §59` fixes no list | Seed per pilot organization once the vocabulary is known | Deferred |
| GAP-025 | Archive | Only the conflict path of `11-P2 §14` is implemented; the "explicitly approved controlled cascade" alternative is not | Operators must archive descendants manually, bottom-up | The frozen source specifies no cascading algorithm and P2 deliberately prefers the safe conflict | Add a controlled cascade only with an approved ADR | By-design (ADR-0015) |
| GAP-026 | API surface | No dedicated hierarchy/tree endpoint exists | The hierarchy is read one level at a time through parent filters | `11-P2 §51`: a new tree endpoint is added only if the existing contract proves insufficient | Revisit only if the frozen contract proves insufficient | By-design / informational |
| GAP-027 | Performance | Academic list/search/child-lookup performance and the `§115` observability acceptance are not evidenced on realistic data | No staged load numbers; correctness and indexes are verified, latency is not | `11-P2 §113/§114/§115` require realistic/staging data before declaring performance complete | Performance phase / staging environment | Open |
| GAP-028 | Evidence | The generated OpenAPI document is not retained as an artifact | Endpoints are annotated (`@Operation`) but no captured spec file exists | `11-P2 §130` retains OpenAPI output as exit evidence | Capture in CI once CI exists (GAP-008) | Open |
| GAP-029 | Repository layer | `11-P2 §25` repository query methods (`findByStatus`, `search`, `searchByExamId`, `searchByCodeOrName`, …) are declared but the list endpoints use JPA Specifications | Some declared query methods are currently unused | `§25` lists them as required repository queries; `§43/§44` filtering/sorting favoured Specifications | Consolidate the two query paths when the layer is next touched | Informational |
| GAP-030 | Concurrency | Academic entities have no `@Version` column and no idempotency mechanism | Concurrent updates are last-write-wins within a record; duplicate creates surface as 409 | `11-P2 §49/§50`: a version column and an idempotency table are explicitly not added in P2 | Add only with formal approval | By-design / informational |
| GAP-031 | Rendering | KaTeX server-side rendering is performed at the Next.js rendering boundary; the Java module emits sanitized HTML with controlled math placeholders (`<span class="katex-tex" data-tex="…">`) | KaTeX itself is not executed inside the JVM | KaTeX is a JavaScript renderer and no Java KaTeX exists in the frozen stack (`08-CDUI §41, §77`) | Revisit if a server-side JS rendering tier is introduced | By-design (ADR-0016) |
| GAP-032 | API surface | No endpoint edits a draft version's canonical content; a change requires a new version | Authors cannot correct a draft in place | The frozen endpoint inventory has no `PUT` for version content (`12-P3 §24, §85`) | Add only with contract review | By-design / informational (ADR-0016) |
| GAP-033 | API surface | Question-tag association is exposed through `PUT /questions/{id}` with `tagIds`; there is no standalone question-tag endpoint | Tags are managed with question metadata rather than a dedicated resource | The contract defines no question-tag endpoint (`12-P3 §49, §50`) | Add a dedicated surface only with contract review | By-design (ADR-0016) |
| GAP-034 | Assets | Protected asset streaming relies on the frozen files endpoint delivered with Delivery | P3 stores metadata and validates ownership but does not stream asset bytes | `05-Contract §61`, `08-CDUI §45` place file download in the delivery/files boundary | With Delivery | Deferred |
| GAP-035 | Import | Bulk content import (`12-P3 §116-§118`) is not implemented | Pilot content is authored through the API; only templates are seeded | P3 supports import only where it directly serves pilot onboarding | Pilot content provisioning | Deferred |
| GAP-036 | Authorization | Content permission codes are implementation candidates from `12-P3 §128`; the API contract names only some of them | Some seeded codes may need realignment with the Identity catalog | The permission entity is configuration-driven | Realign with Identity catalog (with GAP-021) | Open |
| GAP-037 | Caching | No Redis cache is added for published question content | Published reads hit PostgreSQL each time | `12-P3 §62/§131` make caching optional; correctness-first | Optional hardening when load justifies it | Deferred (ADR-0016) |
| GAP-038 | Performance | Content list/detail/publish/render performance (`§172`) and observability (`§147/§149`) are not measured on realistic data | No latency/throughput evidence | Requires staging data | Performance phase / staging | Open |
| GAP-039 | Migrations | Content tables are `V6` and seeds `V7`, not the recommended `V4`/`V13` | Numbering differs from the recommended list | Earlier phases used `V2`-`V5`; the schema document says numbering may differ | None (informational) | Resolved / informational (ADR-0016) |
| GAP-040 | Delivery boundary | `delivery_policy` (Delivery-owned) is created in migration `V8` ahead of P5 | The Delivery phase inherits an already-created table | The frozen `assessment_version.delivery_policy_id` FK requires it | Delivery phase adopts and owns it | By-design (ADR-0017) |
| GAP-041 | Filtering | The assessment list `examId` filter is unsupported: the frozen schema has no path from an assessment to an exam | A supplied `examId` returns a clear 400 rather than filtering | The `assessment` table has no exam column | Revisit only with a schema change | Open |
| GAP-042 | Errors | `QUESTION_POOL_SHORTAGE` detail (requested/available/deficit) is conveyed in the message, not as separate fields | Callers parse the numbers from the message text | Platform `ApiError` shape is field/code/message only | Add structured fields only if the envelope changes | By-design |
| GAP-043 | API surface | No assessment-version mutation endpoint exists; engine-type immutability is enforced and verified at the service boundary | Engine changes after publication are impossible via the API | The frozen contract has no `PUT` for assessment versions | Add only with contract review | By-design (ADR-0017) |
| GAP-044 | Scheduling | Schedule "close" maps to status `INACTIVE`; there is no dedicated `CLOSED` status | A closed schedule is `INACTIVE`; runtime state is derived from status and times | `13-P4 §71` forbids a second persistent status field | Revisit with approval | By-design / informational |
| GAP-045 | Migrations | Assessment is `V9`/`V10` (delivery_policy `V8`), not the recommended `V5`/`V104`/`V105` | Numbering differs from the recommended list | Earlier phases used `V2`-`V7`; numbering may differ | None (informational) | Resolved / informational |
| GAP-046 | Runtime | P4 contains no Start Exam, snapshot creation, `StudentAttempt`, or `AttemptQuestion` | Runtime execution is delivered by P5 | `13-P4 §128-§130` excludes runtime data | P5 Delivery | By-design (ADR-0017) |
| GAP-047 | Selection | The "least-recently-used in prior assessments" tie-break is approximated by question-version id ascending | Ordering is deterministic but not LRU-accurate | Determinism is the frozen requirement (`§46-§47`) | Implement LRU when prior-use tracking is added | Deferred |
| GAP-048 | Authorization | Assessment permission codes are `13-P4 §145` candidates | Codes may need realignment with the Identity catalog | Permission entity is configuration-driven | Realign with Identity catalog (with GAP-021/036) | Open |
| GAP-049 | Frontend | The P4 frontend is a lean slice: blueprint/assessment lists and a validation/publication panel only | The full builders (`§97-§110`) — question picker, rule builder, hybrid UI, version editor, schedule UI, collection UI — are authoring-by-API only | Scope trimming for this slice | Complete the builders in a follow-up | Partial / Open |
| GAP-050 | API surface | No archive endpoints exist for blueprint, assessment, or collection | `BLUEPRINT_ARCHIVE` is seeded but the frozen contract defines no `DELETE` for these resources | `05-Contract §36-§43` lists no archive route | Add only with contract review | By-design / informational |
| GAP-051 | Blueprint validation | Blueprint `language.languageIds` are not verified against the Academic Language catalog at publication | An unknown language id could be configured in a published blueprint | `13-P4 §83` lists `verifyLanguage` as a required Academic contract operation | Verify language ids at blueprint publication | Open |
| GAP-052 | Publication | Publication validates the blueprint configuration but does not require the referenced BlueprintVersion to itself be published | A version could bind a draft blueprint version | `13-P4 §22/§27/§53` do not explicitly require blueprint publication | Decide and document the rule, then enforce if required | Open |
| GAP-053 | Performance | P4 validation, selection, and preview latency is not measured (extends GAP-038) | No P4 latency/throughput evidence | `13-P4 §170-§172` require measurement on realistic data | Performance phase / staging | Open |
| GAP-054 | Caching | No Redis cache is added for assessment read models | Published assessment reads hit PostgreSQL each time | `13-P4 §137/§138` make caching optional | Optional hardening when load justifies it | Deferred |
| GAP-055 | Migrations | `background_job` (Infrastructure-owned) is created in `V12` ahead of its phase | Delivery inherits an already-created table | The evaluation handoff (`14-P5 §67`) has no other durable mechanism | Infrastructure phase adopts it | By-design (ADR-0018) |
| GAP-056 | Authorization | The frozen Identity catalogue defines no Delivery permission codes | Minimal operational codes (`DELIVERY_PREPARE_ATTEMPTS`, `DELIVERY_ATTEMPT_VIEW_ANY`, `DELIVERY_OPERATE`) are seeded | `14-P5 §83` says codes are not invented in Delivery | Realign with the Identity catalogue (with GAP-021/036/048) | Open |
| GAP-057 | Eligibility | The pilot candidate population is the ACTIVE `STUDENT`-role operational set via a new Identity `CandidateDirectory` contract | `AccessGrant`-driven population is not yet implemented | `14-P5 §19` makes this a documented P5 design decision; Commerce is P9 | Adopt `AccessGrant` population when Commerce lands | Deferred |
| GAP-058 | Frontend | The P5 runtime frontend is a lean slice: one `/exam/[attemptId]` runner with autosave, ETag handling, palette state, timer, and submit | Richer runtime UX (multi-tab guard, offline queue, section-timer UI, dedicated palette screen) is not built | Scope trimming for this slice | Complete runtime UX in a follow-up | Partial / Open |
| GAP-059 | Performance | P5 verifies start-burst race safety with in-process concurrency tests, not the 5,000-user staging load (`§106-§113`) | No p95/p99, DB CPU, connection, lock-wait, or WAL evidence | Requires staged data and a load harness | Performance phase / staging (extends GAP-038/053) | Open |
| GAP-060 | Timer | The pilot implements only the global timer (`started_on` + duration) | Section-wise timing needs new persisted state | `14-P5 §58` forbids silently adding a timer table | Schema amendment before enabling | Deferred |
| GAP-061 | Migrations | Delivery is `V11`/`V12`/`V13`, not the recommended `V6`/`V12`/`V13` | Numbering differs from the recommended list | Earlier phases used `V2`-`V10`; the schema document says numbering may differ | None (informational) | Resolved / informational |
| GAP-062 | Timer enforcement | The server derives and returns the deadline, but protected runtime mutations are not rejected after the deadline, and auto-submit on expiry is not implemented | An expired attempt is only finalized when the student submits; late response saves are not blocked server-side | `14-P5 §56` requires non-submission state-changing actions to be rejected after the deadline (distinct from the section-timer gap, GAP-060) | Add deadline enforcement to protected mutations plus the expiry auto-submit path | Open |
| GAP-063 | Navigation | Navigation policy is resolved and returned, and the UI honours it, but the frozen contract exposes no navigation endpoint | Section/question transitions are not server-round-tripped, so the backend cannot reject an out-of-policy transition | `05-Contract §62` defines no navigation route; `14-P5 §36` describes validation without a frozen endpoint | Add only with contract review | By-design / open |
| GAP-064 | Authorization | `DELIVERY_ATTEMPT_VIEW_ANY` is seeded but no staff cross-user read path is exposed; `GET /attempts/{id}` and the question/response reads are self-scoped only | Staff/operations cannot yet read another student's runtime attempt through the API | `14-P5 §83` marks staff access "according to permission"; no staff read route is frozen | Implement the permission-gated staff read when required | Open |
| GAP-065 | Session lifecycle | Assessment-session expiry/logout is not swept by a worker, and attempt expiry versus session expiry are not separated | A session ends only on submit or an explicit `/sessions/{id}/end`; a stale session remains ACTIVE indefinitely | `14-P5 §90/§91` describe a session expiry worker; `assessment_session` has no expiry column | Add a derived-expiry sweep (like the login-session job) | Open |
| GAP-066 | Idempotency | `Idempotency-Key` is neither required nor stored for submit; idempotency relies on the conditional `IN_PROGRESS -> SUBMITTED` transition and the `EVALUATION` job lookup | Replaying a submit is safe for state, but there is no request-level dedupe record | `05-Contract §17/§18` mention `Idempotency-Key`; `14-P5 §47` leaves the mechanism to implementation | Add request-level dedupe if a non-idempotent effect is introduced | By-design / open |
| GAP-067 | Listing | `GET /attempts` filters, sorts, and paginates in memory over the caller's attempts rather than a database-side page query | Acceptable for a single student's history; not suitable for a large staff-scoped listing | `14-P5 §84` requires indexed, projectional queries; the pilot's per-user set is small | Convert to a database-paged projection when staff listing is needed | Open |
| GAP-068 | Delivery policy | Delivery policies are resolved at runtime, but there is no authoring API; only the seeded `DEFAULT` policy exists | New policies cannot be created through the API | No `delivery_policy` endpoint is frozen (`14-P5 §70`) | Manage policy rows operationally, or add an endpoint with contract review | By-design / informational |
| GAP-069 | Time tracking | `timeSpentSeconds` is client-supplied and only range-bounded server-side; there is no server-measured per-question time | Persisted question time is an estimate, not authoritative | `14-P5 §46` describes it as a client measurement persisted on save; no timing table is permitted | Accept as an estimate; add analytics validation if needed | By-design / informational |

## 2. Deliberate non-goals (not gaps)

These are out of scope by specification and are listed so they are not mistaken for omissions:

- P0/P1: Organization / user / role / session administration **screens** (the API and
  permission-aware UI infrastructure exist; a full admin UI does not).
- P0/P1: SSO, OAuth, 2FA, biometric authentication, device trust, multi-tenant authorization.
- P1: Password-change endpoint (the contract defines only forgot/reset).
- Business modules still unimplemented: Evaluation, Analytics, Commerce, Communication,
  Administration.
- P2: Academic master **bulk import** and other tooling are not in the P2 task list.
- P2: Academic data is intentionally not organization-scoped (single-organization deployment;
  `04-Schema §62`).
- P3: A bulk content-import platform and a Content-specific search engine (Elasticsearch/OpenSearch)
  are not implemented; search uses PostgreSQL.

## 3. Remediation sequencing

```text
P0-12 CI/CD + P0-13 secret scan      -> closes GAP-008, GAP-010, GAP-028
P0-09 Docker + P0-10 Edge + P0-14    -> closes GAP-007, GAP-015
P0-11 Observability stack            -> closes GAP-009
Hardening phase                      -> closes GAP-005, GAP-011, GAP-012, GAP-013
P10 Communication                    -> closes GAP-002
P11 Administration                   -> closes GAP-001
Architecture confirmation            -> closes GAP-006
Identity catalog alignment           -> realigns GAP-021, GAP-036
Pilot vocabulary known               -> closes GAP-024
Staging performance phase            -> closes GAP-027, GAP-038
Content caching hardening            -> closes GAP-037 (optional)
Delivery / files endpoint            -> closes GAP-034
Pilot content provisioning           -> closes GAP-035
P9 Commerce / AccessGrant            -> closes GAP-057
Staging load harness                 -> closes GAP-059 (with GAP-038/053)
Identity catalog alignment           -> realigns GAP-056 (with GAP-021/036/048)
Deadline enforcement + auto-submit   -> closes GAP-062
Session expiry sweep                 -> closes GAP-065
Staff attempt read path              -> closes GAP-064
Contract review (navigation/policy)  -> closes GAP-063, GAP-068
Database-paged attempt listing       -> closes GAP-067
```

## 4. Raising a new gap

If an implementation task discovers a missing or contradictory frozen contract:

1. Stop the affected task.
2. Add an entry here using the same columns.
3. If the resolution would change a frozen contract, open an ADR and route it through the
   architecture-change process — do not change the contract locally.

## Document status

- **Status:** Living register.
- **Owner:** Engineering.
- **Reviewed with:** `docs/implementation-status.md`, `docs/decisions/ADR-0010`–`ADR-0018`,
  `docs/testing/p1/README.md`, `docs/testing/security/p1/README.md`, `docs/testing/p2/README.md`,
  `docs/testing/p3/README.md`, `docs/testing/p4/README.md`, `docs/testing/p5/README.md`.
