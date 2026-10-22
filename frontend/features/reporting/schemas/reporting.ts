/**
 * Reporting response and request schemas ({@code specs/22-P13-Reporting-V1.5.md §14, §24, §28, §29}).
 *
 * <p>The frozen reporting endpoints answer with a catalog, one of two request outcomes (a
 * synchronous result or an accepted job), a job status, and a history list. Payloads are parsed
 * here so an unexpected shape becomes a validation error rather than a silently empty screen.
 *
 * <p>A job status is a controlled string: any server value that is not a status this UI knows is
 * mapped to {@code UNKNOWN}. It is never silently rendered as COMPLETED or ACTIVE, because a
 * terminal-looking status would stop polling and offer a download that does not exist.
 */
import { z } from "zod";

/** The four job states the frozen status endpoint documents, plus the specification's terminals. */
const KNOWN_REPORT_STATUSES = [
  "QUEUED",
  "RUNNING",
  "COMPLETED",
  "COMPLETED_WITH_ERRORS",
  "FAILED",
  "CANCELLED",
] as const;

/** Report formats the frozen request contract accepts ({@code §14}). */
export const REPORT_FORMATS = ["CSV", "PDF"] as const;

const nullableText = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

const timestamp = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

const integer = z
  .union([z.number(), z.string()])
  .nullish()
  .transform((value) => {
    if (value === null || value === undefined) {
      return null;
    }
    const parsed = typeof value === "number" ? value : Number(value);
    return Number.isInteger(parsed) ? parsed : null;
  });

/** A status that is never inferred: an unrecognized value becomes {@code UNKNOWN}. */
export const reportStatusSchema = z
  .string()
  .nullish()
  .transform((value) => {
    const normalized = (value ?? "").trim().toUpperCase();
    return (KNOWN_REPORT_STATUSES as readonly string[]).includes(normalized)
      ? normalized
      : "UNKNOWN";
  });

/** One advertised report ({@code §11}). A disabled entry is registered but not requestable. */
export const reportCatalogEntrySchema = z.object({
  reportType: z.string(),
  category: z
    .string()
    .nullish()
    .transform((value) => (value ?? "UNCATEGORIZED").trim().toUpperCase()),
  displayName: z
    .string()
    .nullish()
    .transform((value) => value ?? "Untitled report"),
  description: nullableText,
  supportedFormats: z
    .array(z.string())
    .nullish()
    .transform((value) => {
      const formats = (value ?? [])
        .map((format) => format.trim().toUpperCase())
        .filter((format) => (REPORT_FORMATS as readonly string[]).includes(format));
      const unique = Array.from(new Set(formats));
      // Every registered report advertises at least one format; CSV keeps the selector usable if
      // an older deployment omits the field.
      return unique.length > 0 ? unique : ["CSV"];
    }),
  permission: nullableText,
  filterSchema: z
    .array(z.string())
    .nullish()
    .transform((value) => (value ?? []).map((key) => key.trim()).filter((key) => key.length > 0)),
  executionMode: z
    .string()
    .nullish()
    .transform((value) => {
      const normalized = (value ?? "").trim().toUpperCase();
      return normalized === "SYNC" || normalized === "ASYNC_PREFERRED" ? normalized : "UNKNOWN";
    }),
  // The catalog always sends the flag; a missing flag is treated as advertised.
  enabled: z
    .boolean()
    .nullish()
    .transform((value) => value ?? true),
});

/** A report row is a flat string map; a non-string cell is rendered as text, never evaluated. */
const reportRowSchema = z.record(z.unknown()).transform((row) => {
  const normalized: Record<string, string> = {};
  for (const [key, value] of Object.entries(row)) {
    normalized[key] = value === null || value === undefined ? "" : String(value);
  }
  return normalized;
});

/** An executive metric always carries its definition ({@code §70}). */
export const reportMetricSchema = z.object({
  name: z.string(),
  value: z
    .union([z.string(), z.number(), z.boolean()])
    .nullish()
    .transform((value) => (value === null || value === undefined ? "" : String(value))),
  definition: nullableText,
});

/** The synchronous result for a small bounded report ({@code §5, §24}). */
export const reportResultSchema = z.object({
  reportType: z
    .string()
    .nullish()
    .transform((value) => value ?? "UNKNOWN"),
  format: z
    .string()
    .nullish()
    .transform((value) => (value ?? "").trim().toUpperCase() || "UNKNOWN"),
  rowCount: integer,
  artifactName: nullableText,
  downloadUrl: nullableText,
  rows: z.array(reportRowSchema).default([]),
  metrics: z.array(reportMetricSchema).default([]),
  freshnessNote: nullableText,
});

/** Job status exposes safe fields only — no stack traces or infrastructure identifiers (§28). */
export const reportJobSchema = z.object({
  jobId: z.string(),
  reportType: nullableText,
  status: reportStatusSchema,
  format: z
    .string()
    .nullish()
    .transform((value) => (value ? value.trim().toUpperCase() : null)),
  errorCode: nullableText,
  errorMessage: nullableText,
  artifactName: nullableText,
  downloadUrl: nullableText,
  queuedOn: timestamp,
  completedOn: timestamp,
});

/** One history row ({@code §29, §30}), served from {@code background_job}. */
export const reportHistoryEntrySchema = z
  .object({
    jobId: z.string(),
    reportType: z.string().nullish(),
    // The frozen history projection names the same values reportName / generatedBy / generatedOn;
    // both spellings are accepted so a catalog rename cannot blank the history table.
    reportName: z.string().nullish(),
    initiatedBy: z.string().nullish(),
    generatedBy: z.string().nullish(),
    queuedOn: z.string().nullish(),
    generatedOn: z.string().nullish(),
    completedOn: z.string().nullish(),
    status: reportStatusSchema,
    downloadUrl: z.string().nullish(),
  })
  .transform((value) => ({
    jobId: value.jobId,
    reportType: value.reportType ?? value.reportName ?? "UNKNOWN",
    initiatedBy: value.initiatedBy ?? value.generatedBy ?? null,
    queuedOn: value.queuedOn ?? value.generatedOn ?? null,
    completedOn: value.completedOn ?? null,
    status: value.status,
    downloadUrl: value.downloadUrl ?? null,
  }));

/** The accepted (queued) response ({@code §24}). Presence of {@code jobId} is the discriminator. */
export const reportAcceptedSchema = z.object({
  jobId: z.string().min(1),
  status: reportStatusSchema,
});

/** The request body accepted by {@code POST /reports} ({@code §14, §105}). */
export const reportRequestSchema = z.object({
  reportType: z.string().trim().min(1, "A report type is required"),
  filters: z.record(z.string()).default({}),
  format: z.enum(REPORT_FORMATS),
});

export type ReportCatalogEntry = z.infer<typeof reportCatalogEntrySchema>;
export type ReportMetric = z.infer<typeof reportMetricSchema>;
export type ReportResult = z.infer<typeof reportResultSchema>;
export type ReportJob = z.infer<typeof reportJobSchema>;
export type ReportHistoryEntry = z.infer<typeof reportHistoryEntrySchema>;
export type ReportRequestInput = z.infer<typeof reportRequestSchema>;
export type ReportFormat = (typeof REPORT_FORMATS)[number];

/** The two possible answers to a report request ({@code §24, §25}). */
export type ReportRequestOutcome =
  | { kind: "completed"; result: ReportResult }
  | { kind: "queued"; jobId: string };

/**
 * Distinguishes a synchronous result from an accepted job by the presence of {@code jobId}.
 *
 * <p>This is the only place the two shapes are told apart, so the caller never has to guess.
 */
export function parseReportRequestResponse(payload: unknown): ReportRequestOutcome {
  const accepted = reportAcceptedSchema.safeParse(payload);
  if (accepted.success) {
    return { kind: "queued", jobId: accepted.data.jobId };
  }
  return { kind: "completed", result: reportResultSchema.parse(payload) };
}

/** The UUID shape the backend enforces for identifier filters ({@code §15}). */
export const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuid(value: string): boolean {
  return UUID_PATTERN.test(value.trim());
}

/**
 * Converts a {@code datetime-local} value to the ISO-8601 instant the API expects ({@code §16}).
 *
 * <p>The browser value is local time; the backend stores UTC instants, so the conversion happens
 * once here and the original local text is never sent.
 */
export function toIsoInstant(value: string): string | undefined {
  if (!value) {
    return undefined;
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString();
}
