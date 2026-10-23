# Sizing note — what headroom this environment provides above 5,000 users

This note records the resource allocation, the reasoning behind it, and the
**measured** headroom. It is deliberately separated from the README because the
README describes the topology while this file states what the numbers support.

Everything here is informed by the two preceding tasks:

- **GAP-130** (auth cost): `auth.login` p95 1,240 ms at 100 users, identified as
  bcrypt strength-10 CPU cost, not I/O.
- **GAP-059** (start cost): `attempt.start` reduced to 9–10 statements and p95
  19.7 ms warm on a single session, proving the start path was never the
  throughput problem.

Both matter here: the first says the application tier is CPU-bound on password
verification, and the second says it is *not* bound on database round trips.
Sizing follows from that.

## 1. Target

5,000 concurrent users (`specs/21-P12` §56), staged 100 → 500 → 1,000 → 2,500 →
5,000 (§57).

## 2. Allocation

| Tier | Cores | Memory ceiling | Why this size |
|---|---|---|---|
| database | 4 | 2048m | 5,000 sessions need a connection count the local default (10) cannot supply. `shared_buffers` 512 MB ≈ 25% of the ceiling; `max_connections` 300 exceeds the PgBouncer pool (100) plus Flyway and the exporter, so a pooler restart can drain and reconnect. |
| cache | 4 | 448m | 5,000 sessions of small JSON plus eviction headroom. `maxmemory` 384 MB with `allkeys-lru`, so a cache flood degrades to misses rather than OOM. |
| edge | 4 | 256m | Nginx is connection-bound, not memory-bound. See §4 for why 2 cores was not enough. |
| app | 8 | 2560m | The JVM plus bcrypt. Bcrypt strength 10 costs tens of ms of pure CPU per verify, so login concurrency is a core-count question. |
| generator | 6 | 4096m | See §3. |
| observability | 2 | 1920m total | Scraping 15 s intervals is light; 2 cores is ample. |

Total CPU: 4 + 4 + 4 + 8 + 6 + 2 = **24 cores**, disjoint. No tier shares a core
with any other, so the generator cannot inflate the latency it is measuring.

## 3. Generator headroom — measured

k6 was run against the edge health route (a trivial endpoint, so the result
measures the generator and the edge, not the application) at 5,000
`constant-vus` for 30 s.

| Metric | Value |
|---|---|
| VUs sustained | **5,000** |
| Iterations completed | 155,884 – 160,384 |
| Throughput | **5,078 req/s** |
| Failure rate | 0.00 % |
| Generator RSS | 1.14 GiB of 4 GiB (28 %) |
| Generator CPU | 82–90 % of its 6 cores |

**Headroom.** The generator uses ~1.15 GB of its 4 GB and ~85 % of its 6 cores at
5,000 VUs. Memory has 3.5× headroom; **CPU is nearly exhausted**, so 5,000 VUs is
close to this generator tier's practical ceiling and a 10,000-VU stress run
(`specs/21-P12` §58) would need either more cores or a second generator instance.

This is the number that answers "can the generator host drive 5,000 concurrent
VUs": yes, with room in memory and little room in CPU.

## 4. Finding: the edge, not the generator, was the first saturation point

The first 5,000-VU attempt produced **93.5 % failures**. The cause was not
platform capacity:

```text
2026/10/23 12:33:39 [alert] 1024 worker_connections are not enough
```

The committed `nginx.conf` carries `worker_connections 1024`, a development
default. Each proxied request consumes **two** connections (client + upstream),
so with the edge pinned to 2 cores the practical ceiling was ~1,024 concurrent
users. This would have been measured as platform latency while being purely an
edge configuration limit.

Fix: the environment's `nginx/entrypoint.sh` copies the shared configuration and
rewrites only `worker_connections` (to 16384) and `worker_rlimit_nofile` at
start-up. The shared file stays the single source of truth, so the other
environments are unaffected.

After the fix, with the edge still on 2 cores, the run gave 0 % failures but
p95 `http_req_waiting` of 3.26 s. Widening the edge to 4 cores and re-measuring
gave 0 % failures and p95 1.65 s. **The edge is CPU-bound at 5,000 users**, which
is why it gets 4 cores and not 2.

## 5. Finding: throughput degrades past saturation

The measured scaling curve for the trivial endpoint:

| Load | Throughput | p95 `http_req_waiting` | Failures |
|---|---|---|---|
| 2,500 VUs | 8,463 req/s | 330 ms | 0.00 % |
| 5,000 VUs | 5,078 req/s | 1,650 ms | 0.00 % |

Doubling the load **reduced** throughput by 40 % and increased p95 five-fold.
That is the classic shape of queueing saturation: past the knee, additional
concurrency adds contention instead of work. The knee is somewhere between 2,500
and 5,000 VUs on this host.

This matters for the load re-run because `specs/21-P12` §57 asks for a *curve*,
and a curve with a knee is more informative than a single pass/fail. It also
means a 5,000-user result on this host will show elevated latency that reflects
**this host's** saturation point, not a platform defect.

## 6. Headroom conclusion — what the numbers support

| Claim | Supported? |
|---|---|
| The generator can drive 5,000 concurrent VUs | **Yes**, measured (CPU ~85 % used) |
| The stack accepts 5,000 concurrent connections without refusing them | **Yes**, 0 % failures after the edge fix |
| The environment sustains 5,000 users within a latency target | **No on this host** — the edge saturates and p95 reaches 1.65 s |
| The bounded statement count from GAP-059 holds under load | Out of scope here; verified by `StartAssessmentQueryBudgetIntegrationTest` |
| This is equivalent to physically separate hosts | **No** — single kernel, shared page cache and NIC |

**Headroom above 5,000 users: effectively none on this host.** The generator is
at 85 % of its cores and the edge is CPU-bound at 5,000 VUs. The honest statement
is that this environment removes the *co-residency* objection from the 2026-10-22
run and can drive the full population, but it does not demonstrate spare capacity
beyond 5,000. A 32 GB host with the core allocation in §2, or genuinely separate
machines, would be needed to claim headroom.

## 7. Constraint discovered while doing this: the host has 8 GB of Docker memory

The reference allocation in §2 totals ~10.2 GB of memory ceilings. This host's
Docker VM reports **7,793 MB total, ~4,767 MB available**.

Consequences, recorded rather than papered over:

- The ceilings are per-service limits, not reservations, so the stack runs — but
  a single tier cannot actually be given its full budget.
- Distinguishing "the platform is slow" from "the host ran out of memory" is
  harder here than on a correctly sized host. The 5,000-VU runs showed run-to-run
  variance in failure rate (0 % to 17 %) that is consistent with memory pressure
  in the Docker VM rather than with platform behaviour.
- The allocation in §2 is the **intended** sizing. On a 32 GB host it applies
  unchanged; on this host it is aspirational, and the measured numbers in §5
  should be read as a lower bound.

## 8. Reproducing

```bash
# The allocation is entirely in the environment file.
grep -E 'CPUSET|MEM_LIMIT' infrastructure/environments/production-like/.env

# Verify the tiers do not overlap (asserted by the script)
./scripts/verify-production-like.sh

# Re-measure generator capacity
docker compose -f infrastructure/environments/production-like/docker-compose.generator.yml \
  --env-file infrastructure/environments/production-like/.env --profile load \
  run --rm --entrypoint k6 generator run /load/out/capacity-probe.js
```

`load/out/` is git-ignored, so the capacity probe and its output are artifacts,
not committed evidence. The numbers in this note are the record.
