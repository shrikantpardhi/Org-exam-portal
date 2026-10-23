# Production-like environment (GAP-083)

The environment the 5,000-user load re-run is executed against
(`specs/16-P7-Pilot-V1.5.md` §12, `specs/21-P12-Hardening-V1.5.md` §56).

The 2026-10-22 run was taken on a single co-resident development host, so
`docs/testing/p7/load-2026-10-22.md` and `specs/16-P7` §12–§13 both record that
its numbers are not a production capacity claim. This environment exists to
remove that objection.

## Topology

```text
                      ┌──────────────────────────┐
   load generator     │  generator (k6)          │   cores 16-21, 4 GB
   (separate project) │  no DB credential        │
                      └────────────┬─────────────┘
                                   │ HTTP, edge-net only
                      ┌────────────▼─────────────┐
                      │  nginx  (the only        │   cores 4-7,  256 MB
                      │  published surface)      │
                      └───┬──────────────────┬───┘
                          │                  │
              ┌───────────▼──────┐  ┌────────▼─────────┐
              │ frontend (Next)  │  │ backend (Spring) │  cores 8-15, 2.5 GB
              └──────────────────┘  └───┬──────────┬───┘
                                         │          │
                              ┌──────────▼──┐  ┌────▼─────┐
                              │ pgbouncer   │  │  redis   │  cores 0-3
                              │ (transaction│  │ (cache)  │
                              └──────┬──────┘  └──────────┘
                                     │
                              ┌──────▼───────┐
                              │ postgres 16  │  cores 0-3, 2 GB
                              └──────────────┘

   observability: prometheus + grafana + loki + promtail   cores 22-23
                  exporters: postgres, redis, pgbouncer
```

Five hosts, as required by the task: **generator, backend, database, cache,
observability**. The application traffic path is unchanged from the frozen
architecture — client → nginx → backend → pgbouncer → postgres, with Redis as
cache only. Nothing about the application, the edge, the pooler, the database or
the observability stack was redesigned; only its sizing and its isolation are
new.

## Choice of environment: co-resident VMs on one physical host

The task allows multiple VMs, multiple cloud instances, or bare metal. This
implements **multiple VMs on a single physical host**, emulated with Docker
`cpuset` pinning plus hard memory ceilings per service.

**Why.** The alternative — cloud instances — was rejected because the project
carries no cloud account, no Terraform state backend and no billing arrangement,
and inventing one would put a credential and a cost surface into a repository
that currently has neither. A co-resident layout is reproducible on the machine
the work is actually done on, which matters more for a gap whose complaint is
"the environment did not exist".

**What this guarantees.** Each tier is pinned to a disjoint CPU set and has its
own memory ceiling, so the generator cannot steal cycles from the stack it is
measuring and the database cannot be starved by the application. That is the
specific defect in the 2026-10-22 run.

**What this does not guarantee — stated plainly.** Containers on one kernel
still share the page cache, the network stack and the block device. This is
*not* equivalent to physically separate hosts for tail-latency claims: a noisy
neighbour inside the same kernel can still perturb a p99. The results therefore
support a statement of the form "the platform sustained N users without the
generator competing for CPU", **not** "the platform will do N users in
production". A true multi-machine run remains the stronger evidence and is
recorded as the outstanding part of GAP-083.

## Host allocation

The reference host is **24 logical cores / 32 GB**. Every tier is disjoint:

| Tier | Services | Cores | CPU set | Memory ceiling |
|---|---|---|---|---|
| data | postgres, redis, exporters | 4 | `0-3` | 2048m / 448m / 128m each |
| edge | nginx, pgbouncer | 4 | `4-7` | 256m each |
| app | backend, frontend | 8 | `8-15` | 2560m / 512m |
| generator | k6 | 6 | `16-21` | 4096m |
| observability | prometheus, grafana, loki, promtail | 2 | `22-23` | 768m / 384m / 512m / 256m |

Core totals: 4 + 4 + 8 + 6 + 2 = **24**, exactly the host. The generator's set
(`16-21`) shares no core with any application tier. `scripts/verify-production-like.sh`
asserts this disjointness rather than trusting it.

Sizing rationale, including what the first two tasks changed about it, is in
[`sizing.md`](sizing.md).

> **This host is smaller than the reference.** The development host used here has
> 24 cores but a Docker VM limited to **8 GB RAM** (7,793 MB visible), while the
> table above budgets ~10.2 GB of ceilings. The limits are per-service ceilings,
> not reservations, so the stack runs; but memory is the binding constraint on
> this host and the measured headroom in `sizing.md` says so. On a 32 GB host the
> allocation above is the intended one.

## Network layout

Four bridge networks enforce the tier boundaries instead of documenting them:

| Network | Members | Purpose |
|---|---|---|
| `edge-net` | nginx, frontend, backend, generator | the only ingress path |
| `app-net` | backend, pgbouncer | the application data path |
| `data-net` | postgres, redis, exporters, pgbouncer, backend (Flyway) | the data tier |
| `obs-net` | prometheus, grafana, loki, promtail, backend | scraping and dashboards |

A service reaches only what it shares a network with. Consequences that matter:

- The generator is on `edge-net` **only**. It cannot reach PostgreSQL, Redis or
  PgBouncer, and it holds no database credential — so it cannot bypass the
  application's invariants (`load/README.md`).
- Only nginx publishes a port (`EDGE_HTTP_PORT`, default `8090`). PostgreSQL,
  Redis, PgBouncer, the backend and the frontend have **no host port at all**,
  which is the production shape and is asserted by the verification script.
- Prometheus, Grafana and Loki bind to loopback only.

## Bring-up

Run from the **repository root**. The PgBouncer Dockerfile lives outside its
build context, and the Docker builder resolves that path relative to the working
directory.

```bash
# 1. Credentials (never committed)
cp infrastructure/environments/production-like/.env.example \
   infrastructure/environments/production-like/.env
#    then replace every "replace-with-..." value

# 2. Start the stack (data + edge + app + obs)
docker compose \
  -f infrastructure/environments/production-like/docker-compose.yml \
  --env-file infrastructure/environments/production-like/.env \
  --profile data --profile edge --profile app --profile obs up -d --build

# 3. Verify — expect 31/31 assertions
./scripts/verify-production-like.sh

# 4. Seed the dataset and build credentials (see docs/testing/p7/load-run.md)
#    ...

# 5. Run the generator
docker compose \
  -f infrastructure/environments/production-like/docker-compose.generator.yml \
  --env-file infrastructure/environments/production-like/.env \
  --profile load run --rm --entrypoint k6 generator run /load/k6/scenarios.js

# 6. Tear down
./scripts/verify-production-like.sh --down
```

The verification script takes `--up` to start the environment and `--down` to
remove it, mirroring `scripts/verify-stack.sh`.

### Windows note

If you drive the generator from Git Bash, an in-container path argument such as
`/load/k6/scenarios.js` is rewritten to a `C:/...` path before Docker sees it.
Exclude exactly that argument:

```bash
MSYS2_ARG_CONV_EXCL="/load/k6/scenarios.js" docker compose ... run --rm generator ...
```

Do **not** use `MSYS_NO_PATHCONV=1`: it disables conversion for every argument and
mangles the `--env-file` path, failing the run for an unrelated reason.

## What this environment fixes about the previous run

Three things, all of which were measurement artifacts rather than platform
behaviour:

1. **The generator shared the host with the stack.** Now pinned to its own six
   cores, in a separate Compose project.
2. **The worker cap was 200 concurrent database clients**
   (`PGBOUNCER_MAX_CLIENT_CONN=200`, pool 20). Now 2000 clients and a
   100-connection pool, sized in `sizing.md`.
3. **The edge was capped at 1024 connections per worker.** The committed
   `nginx.conf` is a development default: each proxied request consumes two
   connections, so the ceiling was ~1,024 concurrent users and Nginx logged
   `1024 worker_connections are not enough`. The environment's entrypoint
   rewrites only that directive (to 16384) at start-up, keeping the shared
   configuration as the single source of truth. See `nginx/entrypoint.sh`.

## Known limitations

- **Single kernel.** See "What this does not guarantee" above. Tail-latency
  claims from a co-resident run are weaker than from physically separate hosts.
- **This host has 8 GB of Docker memory** against a ~10.2 GB budget, so the
  measured headroom is smaller than the design assumes. See `sizing.md`.
- **No TLS listener.** The edge runs the plaintext listener only, matching the
  other environments (the TLS block in `nginx.conf` ships inert without a
  certificate). Load numbers are therefore not measuring TLS overhead.
- **The dataset must be seeded per environment.** Credentials from a previous
  environment do not apply here; `load/out/credentials.json` must be rebuilt
  against this database.
