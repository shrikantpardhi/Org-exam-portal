# ADR-0015: P2 Academic structure decisions

- **Status:** Accepted
- **Date:** 2026-10-10
- **Phase:** P2 — Academic Structure
- **Frozen source:** `specs/11-P2-Academic-V1.5.md`, `specs/04-Database-Schema-V1.5.md §26`,
  `specs/05-API-Contract-V1.5.md §27`

## Context

P2 implements the Academic master-data hierarchy (Exam, Subject, Chapter, Topic, SubTopic) plus the
Language and Tag masters. The frozen schema and API contract leave several implementation choices
open; this record captures them so they are not silently improvised.

## Decisions

**1. Hierarchy is relational, one level deep.** Each child references only its immediate parent
(`subject.exam_id`, `chapter.subject_id`, `topic.chapter_id`, `sub_topic.topic_id`). No skip-level
foreign keys, no JSON-embedded children (`§10`).

**2. Scoped business codes.** `exam.code` is globally unique; every other hierarchy node is unique
per parent — `(exam_id, code)`, `(subject_id, code)`, `(chapter_id, code)`, `(topic_id, code)`.
`language.code` and `tag.code` are globally unique. The PostgreSQL unique constraint is the final
authority; a race is translated to HTTP 409 (`§16, §48`). Codes are normalized (trim + upper-case)
consistently, documented by tests; database lengths are unchanged.

**3. Parent existence and eligibility.** The service validates the parent before creating a child.
A child may only be created under an **ACTIVE** parent; INACTIVE and ARCHIVED parents are rejected
with 409 `PARENT_NOT_ELIGIBLE` (`§12, §13`). The rule is centralized in `AcademicValidator`, not
duplicated in controllers or repositories.

**4. Archive does not cascade.** The frozen source specifies no cascading archive algorithm, so P2
implements the safe option: archiving a node with non-archived descendants is rejected with 409
`ACADEMIC_DESCENDANTS_EXIST`. The operator must address descendants first (`§14`). `DELETE` maps to
archive (HTTP 204), never physical deletion (`§11`).

**5. Update cannot archive.** `PUT` accepts `ACTIVE`/`INACTIVE` for deactivate/reactivate, but
rejects `ARCHIVED`: archiving must go through the archive operation so the descendant guard applies.
This prevents bypassing decision 4 through a status change.

**6. Migration numbering.** The schema document *recommends* `V3` for academic tables and `V13` for
master data, but states the grouping is "Recommended" and "actual migration numbering may differ".
P1 already used `V2`/`V3`, so Academic is delivered as `V4__create_academic_tables.sql` and
`V5__seed_academic_permissions.sql`. Recorded in `docs/known-gaps.md` (GAP-016, GAP-020).

**7. Permission catalog.** The API contract states no Academic permission strings. The P2 spec's
`§55` list (`EXAM_VIEW` … `TAG_ARCHIVE`) is seeded as **implementation candidates** and granted to
ADMIN; the catalog remains configuration-driven and can be realigned with Identity role
configuration without code change. Tracked in `docs/known-gaps.md` (GAP-021).

**8. Mapping.** A single explicit `AcademicMapper` keeps the entity/DTO boundary in one reviewable
place (the spec lists seven names but does not freeze the count). Audit fields are never returned.

**9. Audit.** Mutations emit structured audit events through the shared
`common.audit.SecurityEventLogger` (`CREATE`/`UPDATE`/`ARCHIVE` with entity name, id, and actor).
The immutable `audit_log` table is still delivered by Administration (`V11`); this is the P1 audit
boundary (GAP-001), unchanged.

**10. Caching.** The spec permits *selective* Redis caching of stable masters. P2 adds **no** cache
in this slice: correctness-first, and the query path is already index-backed. Redis remains optional
(GAP-023).

**11. Lazy hierarchy.** The frontend never preloads the tree. Each level is a child component mounted
only when its parent is expanded, and each child query is the existing parent-filtered list endpoint
(`§51, §52`). No tree endpoint was added.

## Consequences

- The frozen schema and API surface are preserved exactly; no table, column, or path was added.
- Archive is safe and explicit; content taxonomy cannot be changed unexpectedly.
- Academic depends on Identity for RBAC and audit only (acyclic: Identity does not depend on
  Academic).
