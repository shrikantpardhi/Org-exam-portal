---
name: exam-platform-implementation
description: Implement, fix, or extend the Smart Competitive Examination Platform (V1.5). Loads the frozen-contract rules, the gap-lookup workflow, the standard verification commands, and the cheap-model routing table. Use for any coding task in this repository.
whenToUse: When the user asks to implement a phase, close a gap, fix a defect, add a feature, run verification, or investigate a failure in the exam-platform repository.
---

# Exam Platform Implementation Skill

## Read this first

This repository has a **frozen specification** under `specs/` and a **living gap register** at `docs/known-gaps.md`. Almost every mistake in this project is one of two kinds: reading more specification than the task needs, or silently resolving a gap that should have been reported.

This skill prevents both.

## The four-question pre-flight

Answer these before writing any code. If you cannot answer one, stop and read the referenced file.

1. **Which frozen contract does this touch?**
   → `references/frozen-contracts.md` (one page, table only)
2. **Which gap in `docs/known-gaps.md` covers this area?**
   → `scripts/gap-lookup.sh <keyword>`
3. **Which model should run this task?**
   → `references/model-routing.md` (one page, table only)
4. **What proves the change works?**
   → `references/stop-conditions.md` (acceptance + stop rules)

Do not proceed to implementation until all four are answered.

## Context loading discipline

Load **only** what the task depends on. Never re-read the full specification package for a narrow task.

| Task touches | Read only |
|---|---|
| Identity, auth, RBAC | `specs/10-P1-Identity-V1.5.md` |
| Academic masters | `specs/11-P2-Academic-V1.5.md` |
| Question bank, rendering | `specs/12-P3-Content-V1.5.md` §101–§112 + `specs/08` §41–§47 |
| Blueprint, assessment | `specs/13-P4-Assessment-V1.5.md` |
| Delivery, snapshot, ETag | `specs/14-P5-Delivery-V1.5.md` §21–§58 |
| Evaluation, scoring | `specs/15-P6-Evaluation-V1.5.md` §5–§37 |
| Load, evidence | `specs/16-P7-Pilot-V1.5.md` §24–§36 |
| Analytics | `specs/17-P8-Analytics-V1.5.md` |
| Commerce, payments | `specs/18-P9-Commerce-V1.5.md` |
| Communication | `specs/19-P10-Communication-V1.5.md` |
| Administration | `specs/20-P11-Administration-V1.5.md` |
| Hardening, backup | `specs/21-P12-Hardening-V1.5.md` |
| Reports | `specs/22-P13-Reporting-V1.5.md` |

Always read `AGENTS.md` and the relevant entry in `docs/known-gaps.md`. Nothing else by default.

**Do not read** the Master PRD, Domain Model, or Architecture Freeze for a routine task. They are reference, not context. Load them only when the four-question pre-flight reveals an ambiguity the phase spec cannot resolve.

## The implementation loop
1. Pre-flight (four questions)
2. Inspect the existing code in the affected feature folder
3. Plan the smallest correct change
4. Implement
5. Write tests
6. Run scripts/verify.sh
7. Update docs/known-gaps.md (close, open, or annotate)
8. Update docs/implementation-status.md
9. Review the diff
10. Report: files, tests, gaps touched, unresolved issues


If step 3 produces a conflict with a frozen contract, **stop at step 3** and go to `references/stop-conditions.md`.

## Context budget rules

These exist to keep token cost down. Follow them even when you have budget headroom.

- **Read files, not folders.** Never list a directory and read every file. Read the one file the task names.
- **Read the section, not the whole spec.** Use `find` or `sed -n '<start>,<end>p'` to open only the referenced range.
- **Do not re-read a file you read in the same session.** Keep a mental list.
- **Do not re-derive facts that are in `docs/known-gaps.md`.** The register already names the constraint, the impact, and the target phase.
- **Do not restate the plan in prose.** Write the plan once, short, then implement.
- **Do not narrate routine commands.** Run them; report the result, not the intent.

## Model routing (summary)

Full table: `references/model-routing.md`.

Default choices:

| Task shape | Model |
|---|---|
| YAML, Dockerfile, CI config, git, docs | Cheapest available (GLM Flash / Cat Jam) |
| Frontend components, CRUD screens | Cheap fast model (Cat Jam / Ling 3.0 Flash) |
| Backend state, concurrency, transactions | DeepSeek V4 Flash Fast / Laguna S 2.1 |
| Schema amendment, security boundary, product rule | Frontier (Qwen Max / Beam 1.0) — one task only |

Escalate only when the task **cannot** be finished correctly by the cheaper model. Review output before escalating; a retry on a cheap model is still cheaper than one frontier call.

## Scripts

`scripts/context-pack.sh <task-slug>` — produces the minimal file list for a task based on the table above. Prints the exact `sed`/`find` commands to load only the referenced ranges. Use it instead of opening specs by hand.

`scripts/gap-lookup.sh <keyword>` — greps `docs/known-gaps.md` and prints matching rows. Use it before planning, not after.

`scripts/verify.sh` — runs the standard verification: backend `clean verify`, frontend `lint` + `tsc --noEmit` + `test` + `build`. Exits non-zero on any failure. Use it as step 6, never as an afterthought.

## Stop conditions

Full list: `references/stop-conditions.md`.

Stop and report — do not silently resolve — when the task requires any of:

- a new table, column, or index not in `specs/04-Database-Schema-V1.5.md`
- a new endpoint not in `specs/05-API-Contract-V1.5.md`
- a change to the ExamEngine contract or CDUI capability set
- a change to the evaluation correctness source (`answer_key_detail`)
- a change to the AccessGrant authorization model
- a behavioral change to the snapshot readiness or Start Exam boundary
- a product decision not yet recorded in an ADR

## Reporting format

Every task report is exactly this shape. No prose padding.
Files changed: <list>
Migrations: <list or "none">
APIs: <list or "none">
Tests added: <list>
Verification: <pass|fail> (script output summary)
Gaps closed: <GAP-nnn or "none">
Gaps opened: <GAP-nnn or "none">
Blocked: <reason or "none">


## Anti-patterns

- Reading the whole `specs/` folder to "understand the context."
- Re-implementing a helper that already exists in `lib/` or `common/`.
- Writing a test that asserts a mock was called instead of asserting the behavior.
- Silently working around a gap because "it's just one table."
- Using the frontier model for a task the cheap model already finished.
- Reporting "done" without running `scripts/verify.sh`.
- Updating `docs/known-gaps.md` to close a gap without the test that proves it.

