/**
 * Reporting query keys ({@code specs/22-P13-Reporting-V1.5.md §190}).
 *
 * <p>The catalog and the history are keyed separately from a job so a completed job refresh never
 * discards the catalog the operator is browsing.
 */
export const reportKeys = {
  all: () => ["reporting"] as const,
  catalog: () => ["reporting", "catalog"] as const,
  /** Every history list, whatever its limit — used to invalidate after a request. */
  historyAll: () => ["reporting", "history"] as const,
  history: (limit: number) => ["reporting", "history", limit] as const,
  job: (jobId: string) => ["reporting", "job", jobId] as const,
};
