# P7 dataset construction runbook

The ordered frozen-API sequence that builds the reference load-test dataset
(`specs/16-P7-Pilot-V1.5.md` §8, §21-§23). It uses the real endpoints so every business invariant
(published content, published assessment, snapshot preparation) is enforced by the platform rather
than bypassed with SQL (P7 §8: "version-controlled mechanism, not unrestricted production SQL").

Every step is idempotent-by-design or checked before repeat. Record the returned ids in the dataset
manifest.

## 1. Organization, roles, users (Identity, P1)

1. The default organization and roles (`ADMIN`, `STAFF`, `REVIEWER`, `STUDENT`) are seeded by Flyway.
2. `GET /api/v1/roles` → resolve the role ids you need (`STUDENT`, plus `ADMIN`/`STAFF`/`REVIEWER`).
3. For each of the 5,000 students: `POST /api/v1/users` with `{firstName,lastName,email,roles:[<STUDENT role id>]}`.
   Persist `email` and the returned `id`.
4. **Credential provisioning (blocked by GAP-085).** `POST /api/v1/users` assigns a non-usable
   credential and password-reset delivery is not built (GAP-002), so the cohort cannot log in yet.
   The operator must provision load credentials through an out-of-band, version-controlled
   mechanism before the harness can authenticate. Do **not** hand-edit credentials with ad-hoc SQL
   in a production-like database.

## 2. Academic structure (P2)

1. `POST /api/v1/languages`, `POST /api/v1/exams`, `POST /api/v1/subjects` (and `chapters`/`topics`
   if the corpus uses them). Record the ids.

## 3. Question corpus (P3) — 100 questions, representative content (§22)

For each of the 100 questions, using `SINGLE_CHOICE` (or a representative template mix):

1. `POST /api/v1/questions` `{templateId, examId, subjectId, title, content, marks, negativeMarks, difficulty, languageId}`.
2. `GET /api/v1/questions/{id}/versions` → the draft version id.
3. `POST /api/v1/questions/{id}/versions/{versionId}/options` for each option (one correct).
4. `POST /api/v1/questions/{id}/versions/{versionId}/submit-review` (author).
5. `POST /api/v1/questions/{id}/versions/{versionId}/approve` (reviewer).
6. `POST /api/v1/questions/{id}/versions/{versionId}/publish` (admin).

Include plain text, long text, inline math `\( ... \)`, block math `\[ ... \]`, and special
characters so the corpus is not trivially small (§22).

## 4. Blueprint, assessment version, publication (P4)

1. `POST /api/v1/blueprints` → `POST /api/v1/blueprints/{id}/versions` with a section sized to 100
   questions → `POST .../publish`.
2. `POST /api/v1/assessments` → `POST /api/v1/assessments/{id}/versions`
   `{blueprintVersionId, durationMinutes, engineType, deliveryPolicyId}`.
3. `POST /api/v1/assessment-versions/{versionId}/rules` (rule selecting the 100 published questions)
   → `POST /api/v1/assessment-versions/{versionId}/rules/materialize`.
4. `POST /api/v1/assessment-versions/{versionId}/publish`. Record `assessmentVersionId`.

## 5. Schedule and prepared attempts (P5) — 5,000 READY snapshots (§23)

1. `POST /api/v1/assessment-schedules` `{assessmentVersionId, mode:"EXAM", startTime, endTime}`.
   Record `scheduleId`.
2. `POST /api/v1/assessment-schedules/{scheduleId}/prepare-attempts` (the worker prepares the
   snapshots asynchronously).
3. Wait until every candidate attempt is `READY` **before** any start burst:
   `select count(*) from student_attempt where assessment_schedule_id = :id and snapshot_status = 'READY'`
   must equal the prepared count (§23, §72).

## 6. Answer key (P6)

1. `POST /api/v1/answer-keys` `{assessmentVersionId}`.
2. `POST /api/v1/answer-keys/{id}/details` once per question (`NORMAL` + the correct option).
3. `POST /api/v1/answer-keys/{id}/publish`. Record `answerKeyId`.

## 7. Manifest

Record, with the release candidate (§14): organization, role ids, question/version ids,
assessmentVersionId, scheduleId, answerKeyId, expected question count, expected option count, user
count, and the PRNG seed. This manifest is the reproducible dataset definition (§142).
