#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Sample the production-like stack's resource metrics during a P7 ramp step.
#
# Everything is read from Prometheus. That is deliberate: the metrics the P12
# gate asks for (database CPU/WAL/locks, pool utilisation, heap, GC, Redis hit
# rate, worker backlog) are all point-in-time values, and reading them from one
# scrape keeps them mutually consistent. A psql round trip per sample would
# sample different instants, and the PgBouncer image has no psql anyway.
#
# Postgres-level counters come from the postgres exporter; the raw SQL detail
# the exporters do not publish is captured once per step by snapshot-step.sh.
#
# Usage: sample-stack.sh <output.jsonl> [interval-seconds]
# ---------------------------------------------------------------------------
set -u

OUT="${1:?usage: sample-stack.sh <out.jsonl> [interval]}"
INTERVAL="${2:-5}"
PROM="${PROM_URL:-http://127.0.0.1:9091}"

: > "$OUT"

# One query per field. `jq` is not guaranteed on this host, so the single
# "value":[<ts>,"<val>"] pair is extracted with sed.
q() {
    local expr="$1" raw val
    raw="$(curl -s --max-time 8 --get --data-urlencode "query=$expr" "$PROM/api/v1/query" 2>/dev/null)"
    val="$(printf '%s' "$raw" | sed -n 's/.*"value":\[[^,]*,"\([^"]*\)"\].*/\1/p' | head -1)"
    printf '%s' "${val:-null}"
}

while true; do
    TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    {
        printf '{"ts":"%s"' "$TS"
        printf ',"pg_conn_total":%s'        "$(q 'sum(pg_stat_activity_count{datname="exam_platform"})')"
        printf ',"pg_conn_active":%s'       "$(q 'sum(pg_stat_activity_count{datname="exam_platform",state="active"})')"
        printf ',"pg_max_connections":%s'   "$(q 'pg_settings_max_connections')"
        printf ',"pg_locks":%s'             "$(q 'sum(pg_locks_count{datname="exam_platform"})')"
        printf ',"pg_xact_commit_rate":%s'  "$(q 'sum(rate(pg_stat_database_xact_commit{datname="exam_platform"}[1m]))')"
        printf ',"pg_blks_hit_rate":%s'     "$(q 'sum(rate(pg_stat_database_blks_hit{datname="exam_platform"}[1m]))')"
        printf ',"pg_blks_read_rate":%s'    "$(q 'sum(rate(pg_stat_database_blks_read{datname="exam_platform"}[1m]))')"
        printf ',"pg_wal_bytes":%s'         "$(q 'pg_stat_wal_size_bytes')"
        printf ',"pg_deadlocks":%s'         "$(q 'sum(pg_stat_database_deadlocks{datname="exam_platform"})')"
        printf ',"pool_client_active":%s'   "$(q 'sum(pgbouncer_pools_client_active_connections)')"
        printf ',"pool_server_active":%s'   "$(q 'sum(pgbouncer_pools_server_active_connections)')"
        printf ',"pool_server_idle":%s'     "$(q 'sum(pgbouncer_pools_server_idle_connections)')"
        printf ',"pool_maxwait_s":%s'       "$(q 'max(pgbouncer_pools_client_maxwait_seconds)')"
        printf ',"pool_max_client":%s'      "$(q 'pgbouncer_config_max_client_connections')"
        printf ',"jvm_heap_used":%s'        "$(q 'sum(jvm_memory_used_bytes{area="heap"})')"
        printf ',"jvm_heap_max":%s'         "$(q 'sum(jvm_memory_max_bytes{area="heap"})')"
        printf ',"jvm_gc_pause_rate":%s'    "$(q 'sum(rate(jvm_gc_pause_seconds_sum[1m]))')"
        printf ',"jvm_gc_count_rate":%s'    "$(q 'sum(rate(jvm_gc_pause_seconds_count[1m]))')"
        printf ',"jvm_threads":%s'          "$(q 'jvm_threads_live_threads')"
        printf ',"hikari_active":%s'        "$(q 'sum(hikaricp_connections_active)')"
        printf ',"hikari_pending":%s'       "$(q 'sum(hikaricp_connections_pending)')"
        printf ',"hikari_max":%s'           "$(q 'sum(hikaricp_connections_max)')"
        printf ',"redis_hits_rate":%s'      "$(q 'sum(rate(redis_keyspace_hits_total[1m]))')"
        printf ',"redis_misses_rate":%s'    "$(q 'sum(rate(redis_keyspace_misses_total[1m]))')"
        printf ',"redis_mem_used":%s'       "$(q 'redis_memory_used_bytes')"
        printf ',"redis_mem_max":%s'        "$(q 'redis_memory_max_bytes')"
        printf ',"snapshot_backlog":%s'     "$(q 'sum(exam_snapshot_backlog)')"
        printf ',"http_rps":%s'             "$(q 'sum(rate(http_server_requests_seconds_count{application="exam-platform-backend"}[1m]))')"
        printf ',"http_5xx_rate":%s'        "$(q 'sum(rate(http_server_requests_seconds_count{application="exam-platform-backend",status=~"5.."}[1m]))')"
        printf '}\n'
    } >> "$OUT"

    sleep "$INTERVAL"
done
