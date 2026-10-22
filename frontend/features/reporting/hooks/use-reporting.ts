"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef } from "react";

import {
  getReportCatalog,
  getReportHistory,
  getReportJob,
  requestReport,
} from "@/features/reporting/api/reporting-api";
import { reportKeys } from "@/features/reporting/query-keys";
import type { ReportRequestInput } from "@/features/reporting/schemas/reporting";
import type { ApiClientError } from "@/lib/errors";

/** The bounded polling cadence for a queued or running report ({@code §190, §191}). */
export const JOB_POLL_INTERVAL_MS = 2_500;

/**
 * The hard ceiling on job polls: 60 polls at 2.5 s is about two and a half minutes, after which the
 * UI stops asking and shows the last observed state. A job that outlives this is still safe to read
 * from the history screen, but the browser must never poll forever ({@code §190, §191}).
 */
export const MAX_JOB_POLLS = 60;

/**
 * The polling oracle: how long to wait before the next status read, or {@code false} to stop.
 *
 * <p>Polling is bounded twice over. It stops on any terminal status, and it stops at
 * {@link MAX_JOB_POLLS} even if the server keeps reporting a non-terminal status. Both conditions
 * are asserted directly in the tests, which is why this is a pure exported function rather than
 * logic buried inside the hook.
 */
export function pollInterval(status: string, pollCount: number): number | false {
  const normalized = (status ?? "").trim().toUpperCase();
  if (normalized !== "QUEUED" && normalized !== "RUNNING") {
    return false;
  }
  if (pollCount >= MAX_JOB_POLLS) {
    return false;
  }
  return JOB_POLL_INTERVAL_MS;
}

/** The report catalog the caller may request ({@code §11, §190}). */
export function useReportCatalog(enabled = true) {
  return useQuery({
    queryKey: reportKeys.catalog(),
    queryFn: getReportCatalog,
    enabled,
  });
}

/** The caller's report history ({@code §29, §190, §193}). */
export function useReportHistory(limit = 50, enabled = true) {
  return useQuery({
    queryKey: reportKeys.history(limit),
    queryFn: () => getReportHistory(limit),
    enabled,
  });
}

/**
 * One report job, polled only while it is queued or running ({@code §28, §190, §191}).
 *
 * <p>{@code refetchIntervalInBackground} is false so a backgrounded tab stops polling; the interval
 * callback is the single bound and returns {@code false} once the status is terminal or the poll
 * budget is spent.
 */
export function useReportJob(jobId: string | null, enabled = true) {
  const pollCount = useRef(0);
  const trackedJob = useRef<string | null>(null);

  const query = useQuery({
    queryKey: reportKeys.job(jobId ?? ""),
    queryFn: () => getReportJob(jobId ?? ""),
    enabled: enabled && Boolean(jobId),
    retry: false,
    refetchIntervalInBackground: false,
    refetchInterval: (current) => {
      // A different job starts a fresh poll budget; the previous job's count must not carry over.
      if (trackedJob.current !== jobId) {
        trackedJob.current = jobId;
        pollCount.current = 0;
      }
      const next = pollInterval(current.state.data?.status ?? "QUEUED", pollCount.current);
      if (next !== false) {
        pollCount.current += 1;
      }
      return next;
    },
  });

  const status = query.data?.status ?? "QUEUED";
  // True only when the budget is spent on a non-terminal job: the UI must say why it stopped.
  const pollsExhausted = pollCount.current >= MAX_JOB_POLLS && pollInterval(status, 0) !== false;

  return { ...query, pollsExhausted };
}

/** Requests a report and refreshes the history once the server has answered ({@code §14, §24}). */
export function useRequestReport() {
  const queryClient = useQueryClient();
  return useMutation<Awaited<ReturnType<typeof requestReport>>, ApiClientError, ReportRequestInput>({
    mutationFn: (body) => requestReport(body),
    onSuccess: () => {
      // A new job appears in history immediately, even while it is still QUEUED.
      void queryClient.invalidateQueries({ queryKey: reportKeys.historyAll() });
    },
  });
}
