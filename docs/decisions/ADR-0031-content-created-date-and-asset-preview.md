# ADR-0031 — Version created date and asset preview are by-design absent from the frozen contracts

- **Status:** Accepted
- **Phase:** P3 — Content authoring (raised by GAP-154 and GAP-155)
- **Spec:** `specs/12-P3-Content-V1.5.md` §105 (question editor assets), §110 (version history UI),
  §168 (asset shortcodes); `specs/04-Database-Schema-V1.5.md` §27.3 (`question_version`);
  `specs/05-API-Contract-V1.5.md` §29.6, §46 (asset API)
- **Closes:** GAP-154, GAP-155 — as **by-design**, not as implementations
- **Migration:** none

## Context

Both gaps are the same shape: the content-authoring *UI* specification asks for something the frozen
*schema* and *API* contracts do not carry. Each can only be closed by inventing data or an endpoint,
and AGENTS.md rule 20 forbids resolving a frozen-contract gap silently.

## GAP-154 — the version created date does not exist in the frozen schema

`12-P3 §110` says the version history UI displays "version number, **created date**, review state,
published state, marks, negative marks, and difficulty".

`04-Schema §27.3` defines `question_version` with exactly these columns:

```text
id, question_id, version, title, content, marks, negative_marks, difficulty, language_id,
is_published, published_by, published_on, review_status,
submitted_for_review_by, submitted_for_review_on, reviewed_by, reviewed_on, review_remarks
```

**There is no `created_on`.** The table is also deliberately not a `BaseEntity` — the
`QuestionVersion` entity carries a comment recording that the frozen table "has no `status` and no
audit columns". `V6__create_content_tables.sql` confirms the deployed schema matches the spec.

So `VersionListItem` cannot expose a created date: there is nothing to expose. The three candidate
workarounds are all worse than the gap:

1. **Add a `created_on` column.** That edits a frozen table (`04-Schema §27.3`) and needs a migration
   plus a backfill — and the backfill would have to invent a timestamp for every existing version.
   For a display field on one panel, this is a poor trade.
2. **Substitute `submitted_for_review_on`.** It exists, but it is not creation: a version that is still
   `DRAFT` and never submitted would show no date at all, and a version edited for days before
   submission would report the wrong one. That is a silent lie in an audit-facing panel.
3. **Derive it from `audit_log`.** Creation *is* audited, but the audit trail is retention-scoped and
   not a general-purpose read model; a missing or pruned audit row would blank the column, and the
   query would couple a synchronous UI read to the audit store.

**Decision: by-design.** The version history UI shows the review states and dates it actually has —
`submitted_for_review_on`, `reviewed_on`, `published_on` — and states plainly that the frozen schema
records no creation timestamp for a version. The honest presentation of an unavailable field is a
stated absence, not a plausible-looking substitute. It is a candidate for a future schema amendment
alongside other audit-column decisions; recorded in `docs/known-gaps.md` as such.

## GAP-155 — no asset-content route exists in the frozen API

`12-P3 §105` says the editor provides "upload, list, **preview**, and remove", and requires canonical
`[asset: <uuid>]` references rather than filesystem paths in content.

`05-API-Contract §46` freezes exactly three asset operations:

```text
POST   /api/v1/question-versions/{versionId}/assets
GET    /api/v1/question-versions/{versionId}/assets
DELETE /api/v1/question-versions/{versionId}/assets/{assetId}
```

There is no content or download route. `§168` forbids putting a raw path in content, and the `asset`
table's `file_path` is server-side storage detail (`TEXT`), not a client-facing URL.

The gap is real and the temptation is to "just" reconstruct a path. That is the invented workaround
§105 and rule 20 forbid: it would leak the storage layout, bypass whatever authorization a real
content route would carry, and bake a brittle assumption about `STORAGE_PROVIDER` (`local` today,
swappable by configuration) into the frontend.

Adding a content route is a **new endpoint**, and the task constraints say no new endpoint unless
strictly necessary and approved. Preview is a convenience for authors, not a correctness requirement —
publishing does not depend on it, and the author can verify the asset by downloading it through the
storage layer outside the product.

**Decision: by-design.** No endpoint is added. The asset panel keeps list, upload and remove, renders
the canonical `[asset: <uuid>]` shortcode for insertion, and states explicitly that inline preview is
unavailable because the frozen contract exposes no asset-content route — naming the reason rather than
showing a broken image. Existing tests already assert that the panel "states the missing preview route
instead of inventing one"; this ADR records *why* that is the correct behaviour.

If preview is wanted later, the change is a `GET /api/v1/question-versions/{versionId}/assets/{assetId}/
content` route plus an ADR amending `05-API-Contract §46`, authorized against `QUESTION_VIEW` and
serving bytes with a `Content-Type` from `file_type` and `Content-Disposition: inline`. That is a
contained piece of work, but it is a contract amendment and out of scope here.

## Consequences

- Two UI panels continue to state an absence rather than display a fabricated value.
- No schema change and no new endpoint, so no frozen contract is modified by this ADR.
- Both gaps stay visible in `docs/known-gaps.md`, reclassified from "Open" to "by-design (documented)",
  so they are not mistaken for unfinished work — they are decisions with a recorded rationale.

## Verification

- `frontend/features/content/components/version-history.tsx` states the absence and lists the dates
  that do exist.
- `frontend/features/content/components/asset-panel.tsx` states the absence of a content route.
- `frontend/features/content/components/math-asset-review.test.tsx` already asserts the panel "states
  the missing preview route instead of inventing one"; retained.
