/**
 * Reporting API layer ({@code specs/22-P13-Reporting-V1.5.md §14, §22, §28, §29}).
 *
 * <p>Only the frozen reporting endpoints are called. The catalog is read on every visit rather than
 * hard-coded, because a report type must be advertised by the server before it can be requested
 * ({@code §11}). No endpoint accepts SQL, a table name, or a column expression ({@code §106}).
 *
 * <p>The artifact download is binary. It is fetched as a blob through the authenticated client and
 * saved through a short-lived object URL; the bytes are never parsed.
 */
import { z } from "zod";

import {
  parseReportRequestResponse,
  reportCatalogEntrySchema,
  reportHistoryEntrySchema,
  reportJobSchema,
  reportRequestSchema,
  type ReportCatalogEntry,
  type ReportHistoryEntry,
  type ReportJob,
  type ReportRequestInput,
  type ReportRequestOutcome,
} from "@/features/reporting/schemas/reporting";
import { apiClient, apiGet, apiPost, apiUrl } from "@/lib/api-client";

/** The report catalog the caller is permitted to request ({@code §11}). */
export async function getReportCatalog(): Promise<ReportCatalogEntry[]> {
  const result = await apiGet<unknown>("/reports/catalog");
  return z.array(reportCatalogEntrySchema).parse(result.data);
}

/**
 * Requests a report.
 *
 * <p>The server answers either with a synchronous result for a small bounded report or with an
 * accepted job for a large one. {@link parseReportRequestResponse} discriminates the two by the
 * presence of {@code jobId}, so the caller never has to inspect the payload itself ({@code §24}).
 */
export function requestReport(body: ReportRequestInput): Promise<ReportRequestOutcome> {
  const parsed = reportRequestSchema.parse(body);
  return apiPost<unknown>("/reports", parsed).then((result) =>
    parseReportRequestResponse(result.data),
  );
}

/** The safe status of one report job ({@code §28}). */
export async function getReportJob(jobId: string): Promise<ReportJob> {
  const result = await apiGet<unknown>(`/reports/jobs/${jobId}`);
  return reportJobSchema.parse(result.data);
}

/** The caller's report history, newest first, bounded by {@code limit} ({@code §29}). */
export async function getReportHistory(limit = 50): Promise<ReportHistoryEntry[]> {
  const result = await apiGet<unknown>("/reports/history", { limit });
  return z.array(reportHistoryEntrySchema).parse(result.data);
}

/**
 * The absolute, protected download URL for a completed job ({@code §22}).
 *
 * <p>The path itself is frozen; only the origin is added so the link works from the browser. It is
 * resolved against the page origin rather than a build-time constant, so the link stays same-origin
 * under the edge no matter which hostname the deployment is reached at. The endpoint authorizes the
 * request independently of generation, so this URL grants no access on its own.
 */
export function reportDownloadUrl(jobId: string): string {
  return apiUrl(`/reports/jobs/${jobId}/download`);
}

/**
 * Downloads a completed artifact and saves it ({@code §22, §23}).
 *
 * <p>A plain anchor cannot carry the bearer token, so the artifact is fetched through the
 * authenticated client and handed to the browser as a blob. Response bytes are not inspected.
 */
export async function downloadReportArtifact(jobId: string, fileName?: string | null): Promise<void> {
  const response = await apiClient.get<Blob>(`/reports/jobs/${jobId}/download`, {
    responseType: "blob",
  });
  const objectUrl = URL.createObjectURL(response.data);
  try {
    const anchor = document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = fileName && fileName.length > 0 ? fileName : `report-${jobId}`;
    anchor.rel = "noreferrer";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}
