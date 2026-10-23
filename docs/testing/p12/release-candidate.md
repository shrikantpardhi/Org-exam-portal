# Release candidate v1.5.0-rc1 (P12 §11, §140)

- **Recorded:** 2026-10-23
- **Candidate identities:** commit `c966d1c`, tag `v1.5.0-rc1`, two image digests (below)
- **Closes:** GAP-140 (candidate identity), GAP-133 (migration rehearsal at volume)

This file carries the five identifiers `specs/21-P12-Hardening-V1.5.md` §11 requires, plus the
migration rehearsal §140 and GAP-133 ask for. Where a figure is a measurement it is labelled as one;
where something could not be established it says so rather than being rounded up to a claim.

---

## 1. Candidate identity (§11)

| §11 identifier | Value |
|---|---|
| Application version | `1.5.0-SNAPSHOT` (`backend/pom.xml`) |
| Frontend version | `1.5.0` (`frontend/package.json`) |
| Database migration version | `27` (highest applied; 27 of 27 successful) |
| Container image digests | backend `sha256:e1d0152…`, frontend `sha256:7b0c1c7…` (full values in §2) |
| Configuration revision | commit `c966d1c` — `.env` is untracked by design (git-ignored); `.env.example` is the tracked revision |

**Commit**

```text
c966d1c2664eb5bdf5849ab163691ea02a8471f8
H2
Fri Oct 23 13:32:34 2026 +0530
```

**Tag:** `v1.5.0-rc1`, annotated, pointing at `c966d1c`.

The tag is deliberately on `c966d1c` — the commit the two images were built from — and **not** on the
later commit that carries this record. The deliverable order forces this: images must be built and their
digests known before the digests can be written down, so the record can only be committed afterwards.
Tagging the evidence commit instead would have meant moving a tag after the fact, or amending a tagged
commit, both of which the brief forbids. A reader reconstructing the candidate should use the tag for
source and the digests for binaries; this file is explanatory record, not part of the built artefact.

> **Correction to the task brief.** The brief described the working tree as dirty with GAP-126
> uncommitted. When this work started, `git status --porcelain` was **empty** and GAP-126's lock-file
> repair was already in `c966d1c`. There was therefore nothing to commit as deliverable 1, and I did
> not create an empty commit or amend the existing one to manufacture one. The tag was created on the
> existing candidate commit. The commit's message (`H2`) is a poor description of its contents, which
> is recorded here as an unresolved cosmetic issue rather than fixed by rewriting history —
> `AGENTS.md` forbids history rewriting, and the brief explicitly forbids amending once tagged.

---

## 2. Container images

Both built from `c966d1c` with the repository's own multi-stage Dockerfiles, no build-argument
overrides:

```text
docker build -t exam-platform-backend:v1.5.0-rc1  backend/
docker build -t exam-platform-frontend:v1.5.0-rc1 frontend/
```

| Image | Digest | Compressed size |
|---|---|---|
| `exam-platform-backend:v1.5.0-rc1` | `sha256:e1d015257c1b54988b61ee59f53cf9604c8436fe6e6230903655a1d9e93a14b4` | 772 MB |
| `exam-platform-frontend:v1.5.0-rc1` | `sha256:7b0c1c713c7cbaa6861cfc6cfcb5f9c132a9609799a375e7c8f3cd5e7534af2b` | 361 MB |

These are **locally built** digests, not registry digests. They identify the image content produced
from this commit on this machine; they are not yet reproducible by another party pulling from a
registry, because no registry push was performed (not requested, and the task forbids nothing but
does not ask for it either). A registry digest would additionally verify platform and provenance.
Recorded as a limitation rather than implied to be stronger than it is.

Base images: `eclipse-temurin:21-jre` (backend runtime), `node:22-alpine` (frontend), both pinned by
tag, not by digest — a mutable-tag risk for exact reproducibility, noted below.

---

## 3. Migration rehearsal at production-like volume (GAP-133)

### Environment

`local-docker-single-host` — the label this project already uses for its production-like reference
environment (the same label as the P7 load run).

| Property | Value |
|---|---|
| Host | single Windows development host |
| CPU | 24 logical cores |
| RAM | 15.71 GB host; **8 GB** allocated to the Docker VM |
| PostgreSQL | 16.15 (`postgres:16-alpine`), containerised |
| Flyway | 10.22.0 OSS, run from `flyway/flyway:10-alpine` against the compose network |
| Migration source | `backend/src/main/resources/db/migration`, mounted read-only |

**This is not production hardware.** It is production-*like* in data volume and schema, not in
capacity. Timings below are a rehearsal, not a capacity claim (P12 §56, P7 §12).

### Data volume

The two tables V27 touches were inflated to production-scale row counts. Only those two: the
question is DDL behaviour against large tables, which needs realistic row count and distribution, not
a business-valid object graph.

| Table | Rows | Heap | Total with indexes |
|---|---|---|---|
| `background_job` | 4,400,000 | 420 MB | 1,075 MB |
| `notification` | 5,000,000 | 802 MB | 1,369 MB |

Distribution was chosen to be realistic rather than convenient: `background_job` holds a long tail of
terminal `COMPLETED` rows plus a 200k-row live backlog spread across six job types; `notification`
holds 5M rows across 5,000 users, 5% `UNREAD`. The notification rows have real `user` rows behind
them — `fk_notification_user` was **left in force**, not disabled to make seeding easier.

### Result

| Step | Outcome |
|---|---|
| V1 → V26 on an empty database | **26/26 applied**, 0.929 s |
| V27 on the inflated schema | **applied**, `execution time 00:10.407s` (12.68 s wall including container startup) |
| Final state | version `27`, 27/27 successful, no failed or pending migrations |
| Index sizes | `idx_background_job_type_status_queued` 211 MB; `idx_notification_user_status_created` 237 MB |

### The finding that matters: write blocking

GAP-133 asked specifically about **lock behaviour**, and that is where the rehearsal produced a
result worth acting on. `CREATE INDEX` (non-concurrent, which is what V27 uses) takes a `SHARE` lock
that blocks writes for the whole build. Measured on a clone of the inflating volume:

| Build method | Index build duration | Concurrent writer blocked |
|---|---|---|
| `CREATE INDEX` (V27 as written) | ~10.2 s | **9,749 ms** |
| `CREATE INDEX CONCURRENTLY` (out-of-band) | ~12.6 s | **104 ms** |

So on a table this size, deploying V27 through Flyway blocks writes to `background_job` for about
ten seconds — and to `notification` for a similar period. That is not a defect in V27 (ADR-0028
already states the plain form is correct for small tables and records `CONCURRENTLY` as the
production procedure), but it is now **measured rather than assumed**, which is exactly what GAP-133
was opened for. The production deployment step must therefore use the out-of-band `CONCURRENTLY`
build described in ADR-0028 §"Production build note", not a plain `flyway migrate` at this volume.

### Query plans confirm the indexes actually get used at volume

Not merely "the DDL succeeded" — the amended indexes are chosen by the planner on the full dataset:

```text
background_job (4.4M rows):
  Index Scan using idx_background_job_type_status_queued
    Index Cond: ((job_type = 'EVALUATION') AND (status = 'QUEUED'))
    Execution Time: 0.078 ms   Buffers: shared hit/read = 6   (no Sort)

notification (5M rows, real unread rows for the user):
  Index Scan Backward using idx_notification_user_status_created
    Index Cond: ((user_id = …) AND (notification_status = 'UNREAD'))
    Execution Time: 0.146 ms   Buffers: shared read = 24     (no Sort)
```

### Known limitations of this rehearsal

- `VACUUM (ANALYZE)` failed on the inflated tables with `could not resize shared memory segment …
  No space left on device` — the Docker VM's 64 MB `/dev/shm` is too small for parallel vacuum at
  this table size. `ANALYZE` alone succeeded and the planner statistics behind the plans above are
  real. The consequence is that the rehearsal did **not** exercise the visibility-map refresh a
  production `VACUUM` would perform, so buffer counts here are pessimistic (heap pages not marked
  all-visible are visited). This makes the index benefit look no better than it is, never better.
- Single host, 8 GB Docker VM, no concurrency beyond the one writer in the lock probe.
- No PgBouncer in the path for the rehearsal — Flyway connects directly, which is correct for DDL.
- The rehearsal database still exists as `rehearsal` in the compose Postgres volume; it is
  disposable and is not part of the candidate.

---

## 4. What is still not verified

Stated plainly, because the release decision depends on it:

- **The release gate remains NO-GO.** GAP-130 is still the trigger: measured p95 was 4.3× over the
  <100 ms target across the whole P7 ramp. A reproducible candidate does not make a passing candidate.
- **No registry push**, so the digests identify local images only (§2).
- **No staging deployment or smoke test** (P12 §145) was performed for this candidate.
- **Base images are tag-pinned, not digest-pinned**, so a rebuild of the same commit later may not
  produce a byte-identical image.
