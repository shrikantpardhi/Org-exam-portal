import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

import { apiGet } from "@/lib/api-client";
import { ApiClientError } from "@/lib/errors";
import { AttemptResultView } from "@/features/evaluation/components/attempt-result-view";
import { ResultByIdView } from "@/features/evaluation/components/result-by-id-view";
import { ScorecardByResultView } from "@/features/evaluation/components/scorecard-by-result-view";
import { StudentResultsList } from "@/features/evaluation/components/student-results-list";

const mockedGet = vi.mocked(apiGet);

interface Payload {
  message: string;
  data: unknown;
}

function attempt(attemptId: string, attemptStatus = "EVALUATED", startedOn = "2026-10-01T10:00:00Z") {
  return {
    attemptId,
    assessmentVersionId: "v1",
    attemptMode: "EXAM",
    attemptStatus,
    startedOn,
    submittedOn: "2026-10-01T11:00:00Z",
  };
}

function publishedResult(overrides: Record<string, unknown> = {}) {
  return {
    id: "r1",
    evaluationId: "e1",
    attemptId: "a1",
    assessmentVersionId: "v1",
    obtainedMarks: 82.5,
    percentage: 82.5,
    rank: 4,
    percentile: 91.5,
    resultStatus: "PUBLISHED",
    supersededOn: null,
    ...overrides,
  };
}

const notFound = new ApiClientError({
  message: "Not found",
  status: 404,
  code: "NOT_FOUND",
});

function mockRoutes(routes: Record<string, Payload | Error>) {
  mockedGet.mockImplementation((url: string) => {
    const value = routes[url];
    if (value === undefined) {
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    }
    if (value instanceof Error) {
      return Promise.reject(value);
    }
    return Promise.resolve(value as never);
  });
}

function requestedUrls(): string[] {
  return mockedGet.mock.calls.map((call) => String(call[0]));
}

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("student result list", () => {
  it("shows a loading state while the attempt read is in flight", () => {
    mockedGet.mockImplementation(() => new Promise<never>(() => {}));

    renderWithClient(<StudentResultsList />);

    expect(screen.getByRole("status")).toHaveTextContent("Loading your results…");
  });

  it("shows the empty state when no published result exists", async () => {
    mockRoutes({ "/attempts": { message: "Attempts retrieved", data: [] } });

    renderWithClient(<StudentResultsList />);

    expect(await screen.findByText("No published results yet.")).toBeInTheDocument();
    expect(screen.queryByText("0.00")).toBeNull();
  });

  it("shows an error state with a keyboard-operable retry", async () => {
    mockedGet.mockRejectedValue(
      new ApiClientError({
        message: "Results service unavailable",
        status: 503,
        code: "UNAVAILABLE",
      }),
    );

    renderWithClient(<StudentResultsList />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Results service unavailable");
    expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
  });

  it("shows only published results and never queries a non-terminal attempt", async () => {
    mockRoutes({
      "/attempts": {
        message: "Attempts retrieved",
        data: [attempt("a1"), attempt("a2"), attempt("a3", "IN_PROGRESS")],
      },
      "/attempts/a1/result": {
        message: "Result retrieved",
        data: publishedResult({ id: "r1", attemptId: "a1" }),
      },
      "/attempts/a2/result": notFound,
    });

    renderWithClient(<StudentResultsList />);

    expect(await screen.findByRole("article", { name: "Result r1" })).toBeInTheDocument();
    expect(screen.getAllByRole("article")).toHaveLength(1);
    expect(requestedUrls()).not.toContain("/attempts/a3/result");
    expect(screen.queryByText("Result r2")).toBeNull();
  });

  it("hides a superseded result even when the backend answers 200", async () => {
    mockRoutes({
      "/attempts": { message: "Attempts retrieved", data: [attempt("a1")] },
      "/attempts/a1/result": {
        message: "Result retrieved",
        data: publishedResult({ id: "r1", supersededOn: "2026-10-21T10:00:00Z" }),
      },
    });

    renderWithClient(<StudentResultsList />);

    expect(await screen.findByText("No published results yet.")).toBeInTheDocument();
    expect(screen.queryByText("82.50")).toBeNull();
  });
});

describe("single result visibility", () => {
  it("does not expose another student's result when the backend answers 404", async () => {
    mockRoutes({ "/results/r1": notFound });

    renderWithClient(<ResultByIdView resultId="r1" />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This result is not available. Only your own published results can be viewed.",
    );
    expect(screen.queryByText("Obtained marks")).toBeNull();
    expect(screen.queryByText("82.50")).toBeNull();
  });

  it("renders the caller's own published result", async () => {
    mockRoutes({ "/results/r1": { message: "Result retrieved", data: publishedResult() } });

    renderWithClient(<ResultByIdView resultId="r1" />);

    expect(await screen.findByRole("article", { name: "Your result" })).toHaveTextContent(
      "82.50",
    );
  });

  it("renders the attempt-bound result", async () => {
    mockRoutes({
      "/attempts/a1/result": { message: "Result retrieved", data: publishedResult() },
    });

    renderWithClient(<AttemptResultView attemptId="a1" />);

    expect(await screen.findByRole("article", { name: "Your result" })).toBeInTheDocument();
  });

  it("does not expose an unpublished attempt result", async () => {
    mockRoutes({ "/attempts/a1/result": notFound });

    renderWithClient(<AttemptResultView attemptId="a1" />);

    expect(await screen.findByRole("alert")).toHaveTextContent("This result is not available.");
    expect(screen.queryByText("Obtained marks")).toBeNull();
  });
});

describe("scorecard view", () => {
  const scorecard = {
    message: "Scorecard retrieved",
    data: {
      scorecardId: "s1",
      resultId: "r1",
      status: "GENERATED",
      generatedOn: "2026-10-20T10:00:00Z",
      secureDownloadReference: "/api/v1/results/r1/scorecard",
    },
  };

  it("renders the scorecard of a published result", async () => {
    mockRoutes({
      "/results/r1": { message: "Result retrieved", data: publishedResult() },
      "/results/r1/scorecard": scorecard,
    });

    renderWithClient(<ScorecardByResultView resultId="r1" />);

    expect(await screen.findByRole("heading", { name: "Scorecard" })).toBeInTheDocument();
    expect(screen.getByText("Generated")).toBeInTheDocument();
    expect(screen.getByText("/api/v1/results/r1/scorecard")).toBeInTheDocument();
  });

  it("shows an empty state when no scorecard has been generated", async () => {
    mockRoutes({
      "/results/r1": { message: "Result retrieved", data: publishedResult() },
      "/results/r1/scorecard": notFound,
    });

    renderWithClient(<ScorecardByResultView resultId="r1" />);

    expect(
      await screen.findByText("No scorecard has been generated for this result yet."),
    ).toBeInTheDocument();
  });

  it("never requests a scorecard for an unpublished result", async () => {
    mockRoutes({
      "/results/r1": {
        message: "Result retrieved",
        data: publishedResult({ resultStatus: "CALCULATED" }),
      },
    });

    renderWithClient(<ScorecardByResultView resultId="r1" />);

    expect(await screen.findByText("This result is not published yet.")).toBeInTheDocument();
    expect(requestedUrls()).not.toContain("/results/r1/scorecard");
    expect(screen.queryByText("Scorecard")).toBeNull();
  });
});
