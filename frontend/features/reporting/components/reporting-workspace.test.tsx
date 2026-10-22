import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  apiClient: { get: vi.fn() },
  setUnauthorizedHandler: vi.fn(),
  REQUEST_ID_HEADER: "X-Request-Id",
  API_VERSION_PREFIX: "/api/v1",
}));

import { apiGet, apiPost } from "@/lib/api-client";
import { ReportingWorkspace } from "@/features/reporting/components/reporting-workspace";

const mockedGet = vi.mocked(apiGet);
const mockedPost = vi.mocked(apiPost);

const catalogEntry = {
  reportType: "STUDENT_PROFILE",
  category: "STUDENT",
  displayName: "Student Profile",
  description: "Identity and enrolment summary.",
  supportedFormats: ["CSV"],
  permission: "REPORT_STUDENT_READ",
  filterSchema: ["studentId"],
  executionMode: "SYNC",
  enabled: true,
};

const asyncEntry = {
  reportType: "ASSESSMENT_ATTEMPTS",
  category: "ASSESSMENT",
  displayName: "Assessment Attempts",
  description: "Attempts per assessment version.",
  supportedFormats: ["CSV", "PDF"],
  permission: "REPORT_ASSESSMENT_READ",
  filterSchema: ["from", "to"],
  executionMode: "ASYNC_PREFERRED",
  enabled: true,
};

const historyEntry = {
  jobId: "job-history-1",
  reportType: "COMMERCE_PAYMENTS",
  initiatedBy: "user-1",
  queuedOn: "2026-10-21T09:00:00Z",
  status: "COMPLETED",
  downloadUrl: "/api/v1/reports/jobs/job-history-1/download",
};

function renderWorkspace() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ReportingWorkspace scopeNote="Student reports are always scoped to you." />
    </QueryClientProvider>,
  );
}

/** Routes the mocked GET calls by path so catalog, history and job reads stay independent. */
function routeGet(options: { jobStatus?: () => string } = {}) {
  mockedGet.mockImplementation(((url: string) => {
    if (url === "/reports/catalog") {
      return Promise.resolve({
        message: "ok",
        data: [catalogEntry, asyncEntry],
      } as never);
    }
    if (url === "/reports/history") {
      return Promise.resolve({ message: "ok", data: [historyEntry] } as never);
    }
    if (url.startsWith("/reports/jobs/")) {
      const status = options.jobStatus ? options.jobStatus() : "QUEUED";
      return Promise.resolve({
        message: "ok",
        data: {
          jobId: "job-queued-1",
          reportType: "ASSESSMENT_ATTEMPTS",
          status,
          format: "CSV",
          errorCode: null,
          errorMessage: null,
          artifactName: status === "COMPLETED" ? "assessment-attempts.csv" : null,
          downloadUrl: status === "COMPLETED" ? "/api/v1/reports/jobs/job-queued-1/download" : null,
          queuedOn: "2026-10-22T10:00:00Z",
          completedOn: status === "COMPLETED" ? "2026-10-22T10:00:42Z" : null,
        },
      } as never);
    }
    return Promise.reject(new Error(`Unexpected GET ${url}`));
  }) as never);
}

beforeEach(() => {
  vi.clearAllMocks();
  routeGet();
});

describe("reporting workspace", () => {
  it("shows the scope note and the catalog", async () => {
    renderWorkspace();

    expect(screen.getByText("Student reports are always scoped to you.")).toBeInTheDocument();
    expect(await screen.findByText("Student Profile")).toBeInTheDocument();
    expect(mockedGet).toHaveBeenCalledWith("/reports/catalog");
  });

  it("selecting a report shows its filters and its supported formats only", async () => {
    const user = userEvent.setup();
    renderWorkspace();

    expect(screen.queryByLabelText("Student id")).toBeNull();

    const item = (await screen.findByText("Assessment Attempts")).closest("li") as HTMLElement;
    await user.click(within(item).getByRole("button", { name: "Select" }));

    expect(await screen.findByLabelText("From")).toHaveAttribute("type", "datetime-local");
    expect(screen.getByLabelText("To")).toHaveAttribute("type", "datetime-local");

    const format = screen.getByLabelText("Format");
    const options = within(format)
      .getAllByRole("option")
      .map((option) => option.textContent);
    expect(options).toEqual(["CSV", "PDF"]);
  });

  it("submitting a small report renders the completed result rows", async () => {
    const user = userEvent.setup();
    renderWorkspace();

    mockedPost.mockResolvedValueOnce({
      message: "Report generated",
      data: {
        reportType: "STUDENT_PROFILE",
        format: "CSV",
        rowCount: 1,
        artifactName: "student-profile.csv",
        downloadUrl: null,
        rows: [{ student: "Ada Lovelace", status: "ACTIVE" }],
        metrics: [{ name: "Rows", value: "1", definition: "Number of projected rows" }],
        freshnessNote: "Generated from data current as of now.",
      },
    } as never);

    const item = (await screen.findByText("Student Profile")).closest("li") as HTMLElement;
    await user.click(within(item).getByRole("button", { name: "Select" }));
    await user.type(await screen.findByLabelText("Student id"), "11111111-1111-1111-1111-111111111111");
    await user.click(screen.getByRole("button", { name: "Generate report" }));

    expect(await screen.findByText("Ada Lovelace")).toBeInTheDocument();
    expect(screen.getByText(/1 rows/)).toBeInTheDocument();
    expect(screen.getByText("Number of projected rows")).toBeInTheDocument();
    expect(screen.getByText("Generated from data current as of now.")).toBeInTheDocument();

    expect(mockedPost).toHaveBeenCalledWith("/reports", {
      reportType: "STUDENT_PROFILE",
      filters: { studentId: "11111111-1111-1111-1111-111111111111" },
      format: "CSV",
    });
    // A completed synchronous result never starts a job poll.
    expect(mockedGet.mock.calls.some(([url]) => String(url).startsWith("/reports/jobs/"))).toBe(false);
  });

  it("rejects a malformed identifier before calling the API", async () => {
    const user = userEvent.setup();
    renderWorkspace();

    const item = (await screen.findByText("Student Profile")).closest("li") as HTMLElement;
    await user.click(within(item).getByRole("button", { name: "Select" }));
    await user.type(await screen.findByLabelText("Student id"), "not-a-uuid");
    await user.click(screen.getByRole("button", { name: "Generate report" }));

    expect(await screen.findByText("Enter a valid identifier")).toBeInTheDocument();
    expect(mockedPost).not.toHaveBeenCalled();
  });

  it("submitting a large report shows the queued job, polls it, and stops on completion", async () => {
    const user = userEvent.setup();
    let jobReads = 0;
    // The first read reports RUNNING; every later read reports COMPLETED.
    routeGet({
      jobStatus: () => {
        jobReads += 1;
        return jobReads === 1 ? "RUNNING" : "COMPLETED";
      },
    });

    mockedPost.mockResolvedValueOnce({
      message: "Report generation started",
      data: { jobId: "job-queued-1", status: "QUEUED" },
    } as never);

    renderWorkspace();

    const item = (await screen.findByText("Assessment Attempts")).closest("li") as HTMLElement;
    await user.click(within(item).getByRole("button", { name: "Select" }));
    await user.click(await screen.findByRole("button", { name: "Generate report" }));

    // The accepted job is announced and the first status read happens immediately.
    const statusRegion = await screen.findByLabelText("Report job status", {}, { timeout: 3000 });
    await waitFor(() =>
      expect(mockedGet).toHaveBeenCalledWith("/reports/jobs/job-queued-1"),
    );
    expect(within(statusRegion).getByText("Running")).toBeInTheDocument();

    // The queue poll fires without any user action, and a terminal status renders the artifact.
    await waitFor(
      () => {
        expect(statusRegion.querySelector('[data-report-status="COMPLETED"]')).not.toBeNull();
        expect(within(statusRegion).getByRole("link", { name: /Download report/i })).toBeInTheDocument();
      },
      { timeout: 6000 },
    );

    const jobReadCount = mockedGet.mock.calls.filter(([url]) =>
      String(url).startsWith("/reports/jobs/"),
    ).length;
    expect(jobReadCount).toBeGreaterThanOrEqual(2);

    // Polling stops at a terminal status: no further status reads arrive afterwards.
    await new Promise((resolve) => setTimeout(resolve, 3000));
    const settledReadCount = mockedGet.mock.calls.filter(([url]) =>
      String(url).startsWith("/reports/jobs/"),
    ).length;
    expect(settledReadCount).toBe(jobReadCount);
  }, 20000);

  it("renders the history table with a download only for a completed job", async () => {
    renderWorkspace();

    const row = await screen.findByText("COMMERCE_PAYMENTS");
    expect(row).toBeInTheDocument();
    const tableRow = row.closest("tr") as HTMLElement;
    expect(within(tableRow).getByText("Completed")).toBeInTheDocument();
    expect(within(tableRow).getByRole("link", { name: "Download" })).toBeInTheDocument();
    expect(mockedGet).toHaveBeenCalledWith("/reports/history", { limit: 50 });
  });

  it("shows the empty history message when there are no past jobs", async () => {
    mockedGet.mockImplementation(((url: string) =>
      Promise.resolve(
        url === "/reports/catalog"
          ? ({ message: "ok", data: [catalogEntry] } as never)
          : ({ message: "ok", data: [] } as never),
      )) as never);

    renderWorkspace();
    expect(await screen.findByText("No report history yet.")).toBeInTheDocument();
  });

  it("shows a safe error when the catalog cannot be loaded", async () => {
    mockedGet.mockRejectedValue(new Error("boom"));

    renderWorkspace();
    expect(
      await screen.findByText("The report catalog could not be loaded."),
    ).toBeInTheDocument();
  });
});
