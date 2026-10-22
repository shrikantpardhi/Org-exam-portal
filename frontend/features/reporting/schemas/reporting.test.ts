import { describe, expect, it } from "vitest";

import {
  isUuid,
  parseReportRequestResponse,
  reportCatalogEntrySchema,
  reportHistoryEntrySchema,
  reportJobSchema,
  toIsoInstant,
} from "@/features/reporting/schemas/reporting";
import { pollInterval, MAX_JOB_POLLS, JOB_POLL_INTERVAL_MS } from "@/features/reporting/hooks/use-reporting";

/**
 * Reporting schema tests ({@code specs/22-P13-Reporting-V1.5.md §11, §24, §28, §29, §190, §191}).
 *
 * <p>These assert the two rules the UI depends on: an unrecognized job status is never mistaken for
 * a successful one, and a request response is classified by the presence of {@code jobId}.
 */
describe("report catalog schema", () => {
  it("parses a complete catalog entry", () => {
    const parsed = reportCatalogEntrySchema.parse({
      reportType: "ASSESSMENT_ATTEMPTS",
      category: "assessment",
      displayName: "Assessment Attempts",
      description: "Attempts in a period.",
      supportedFormats: ["CSV", "PDF"],
      permission: "REPORT_ASSESSMENT_READ",
      filterSchema: ["from", "to"],
      executionMode: "ASYNC_PREFERRED",
      enabled: false,
    });

    expect(parsed.category).toBe("ASSESSMENT");
    expect(parsed.executionMode).toBe("ASYNC_PREFERRED");
    expect(parsed.supportedFormats).toEqual(["CSV", "PDF"]);
    expect(parsed.filterSchema).toEqual(["from", "to"]);
    expect(parsed.enabled).toBe(false);
  });

  it("tolerates missing and null optional fields without inventing formats", () => {
    const parsed = reportCatalogEntrySchema.parse({
      reportType: "STUDENT_PROFILE",
      displayName: null,
      description: null,
      supportedFormats: null,
      filterSchema: null,
      executionMode: null,
      permission: null,
      category: null,
      enabled: null,
    });

    expect(parsed.displayName).toBe("Untitled report");
    expect(parsed.description).toBeNull();
    expect(parsed.permission).toBeNull();
    expect(parsed.category).toBe("UNCATEGORIZED");
    expect(parsed.executionMode).toBe("UNKNOWN");
    expect(parsed.filterSchema).toEqual([]);
    // A missing format list must not offer a format the report may not support.
    expect(parsed.supportedFormats).toEqual(["CSV"]);
    expect(parsed.enabled).toBe(true);
  });

  it("drops formats that are not part of the frozen request contract", () => {
    const parsed = reportCatalogEntrySchema.parse({
      reportType: "X",
      supportedFormats: ["CSV", "XLSX", "pdf"],
    });
    expect(parsed.supportedFormats).toEqual(["CSV", "PDF"]);
  });
});

describe("report job status schema", () => {
  it("keeps a known status", () => {
    const parsed = reportJobSchema.parse({ jobId: "job-1", status: "RUNNING" });
    expect(parsed.status).toBe("RUNNING");
  });

  it("maps an unrecognized or missing status to UNKNOWN, never to a terminal state", () => {
    expect(reportJobSchema.parse({ jobId: "job-1", status: "DONE" }).status).toBe("UNKNOWN");
    expect(reportJobSchema.parse({ jobId: "job-1" }).status).toBe("UNKNOWN");
    expect(reportJobSchema.parse({ jobId: "job-1", status: "" }).status).toBe("UNKNOWN");
    // COMPLETED must never be the default; polling would stop and a download would be offered.
    expect(reportJobSchema.parse({ jobId: "job-1" }).status).not.toBe("COMPLETED");
  });

  it("parses a failed job with its safe error fields", () => {
    const parsed = reportJobSchema.parse({
      jobId: "job-1",
      reportType: "ASSESSMENT_ATTEMPTS",
      status: "FAILED",
      format: "csv",
      errorCode: "REPORT_RENDER_FAILED",
      errorMessage: "The artifact could not be rendered.",
      queuedOn: "2026-10-22T10:00:00Z",
      completedOn: null,
    });

    expect(parsed.status).toBe("FAILED");
    expect(parsed.format).toBe("CSV");
    expect(parsed.errorCode).toBe("REPORT_RENDER_FAILED");
    expect(parsed.completedOn).toBeNull();
  });
});

describe("report request response discrimination", () => {
  it("classifies an accepted job by the presence of jobId", () => {
    const outcome = parseReportRequestResponse({ jobId: "job-9", status: "QUEUED" });
    expect(outcome).toEqual({ kind: "queued", jobId: "job-9" });
  });

  it("classifies a synchronous result as completed", () => {
    const outcome = parseReportRequestResponse({
      reportType: "STUDENT_PROFILE",
      format: "CSV",
      rowCount: 1,
      artifactName: "student-profile.csv",
      downloadUrl: null,
      rows: [{ student: "Ada" }],
      metrics: [{ name: "Rows", value: 1, definition: "Counted rows" }],
      freshnessNote: "Generated now",
    });

    expect(outcome.kind).toBe("completed");
    if (outcome.kind === "completed") {
      expect(outcome.result.rowCount).toBe(1);
      expect(outcome.result.rows).toEqual([{ student: "Ada" }]);
      expect(outcome.result.metrics[0]?.definition).toBe("Counted rows");
    }
  });

  it("does not treat an empty object as a queued job", () => {
    expect(parseReportRequestResponse({}).kind).toBe("completed");
  });
});

describe("report history schema", () => {
  it("reads the frozen history projection", () => {
    const parsed = reportHistoryEntrySchema.parse({
      jobId: "job-1",
      reportType: "ASSESSMENT_ATTEMPTS",
      initiatedBy: "user-1",
      queuedOn: "2026-10-22T10:00:00Z",
      status: "COMPLETED",
      downloadUrl: "/api/v1/reports/jobs/job-1/download",
    });

    expect(parsed.reportType).toBe("ASSESSMENT_ATTEMPTS");
    expect(parsed.initiatedBy).toBe("user-1");
    expect(parsed.status).toBe("COMPLETED");
    expect(parsed.downloadUrl).toBe("/api/v1/reports/jobs/job-1/download");
  });

  it("accepts the reportName/generatedOn spelling without losing the row", () => {
    const parsed = reportHistoryEntrySchema.parse({
      jobId: "job-2",
      reportName: "COMMERCE_PAYMENTS",
      generatedBy: "user-2",
      generatedOn: "2026-10-22T11:00:00Z",
      status: "weird-state",
    });

    expect(parsed.reportType).toBe("COMMERCE_PAYMENTS");
    expect(parsed.initiatedBy).toBe("user-2");
    expect(parsed.queuedOn).toBe("2026-10-22T11:00:00Z");
    expect(parsed.status).toBe("UNKNOWN");
  });
});

describe("filter helpers", () => {
  it("converts a local datetime value to an ISO instant", () => {
    const iso = toIsoInstant("2026-10-01T10:30");
    expect(iso).toBe(new Date("2026-10-01T10:30").toISOString());
    expect(iso).toMatch(/Z$/);
    expect(toIsoInstant("")).toBeUndefined();
    expect(toIsoInstant("not-a-date")).toBeUndefined();
  });

  it("recognizes a UUID and rejects other identifier text", () => {
    expect(isUuid("11111111-1111-1111-1111-111111111111")).toBe(true);
    expect(isUuid("not-a-uuid")).toBe(false);
    expect(isUuid("")).toBe(false);
  });
});

describe("pollInterval", () => {
  it("polls while queued or running", () => {
    expect(pollInterval("QUEUED", 0)).toBe(JOB_POLL_INTERVAL_MS);
    expect(pollInterval("RUNNING", 5)).toBe(JOB_POLL_INTERVAL_MS);
  });

  it("stops on every terminal status", () => {
    expect(pollInterval("COMPLETED", 0)).toBe(false);
    expect(pollInterval("FAILED", 0)).toBe(false);
    expect(pollInterval("CANCELLED", 0)).toBe(false);
    expect(pollInterval("COMPLETED_WITH_ERRORS", 0)).toBe(false);
  });

  it("never polls an unknown status", () => {
    expect(pollInterval("UNKNOWN", 0)).toBe(false);
    expect(pollInterval("", 0)).toBe(false);
  });

  it("stops after the bounded attempt cap even if the job never finishes", () => {
    expect(pollInterval("RUNNING", MAX_JOB_POLLS - 1)).toBe(JOB_POLL_INTERVAL_MS);
    expect(pollInterval("RUNNING", MAX_JOB_POLLS)).toBe(false);
    expect(pollInterval("RUNNING", MAX_JOB_POLLS + 1)).toBe(false);
  });
});
