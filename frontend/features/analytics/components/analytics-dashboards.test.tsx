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
import { AssessmentAnalyticsDashboard } from "@/features/analytics/components/assessment-analytics-dashboard";
import { OrganizationAnalyticsDashboard } from "@/features/analytics/components/organization-analytics-dashboard";
import { QuestionAnalyticsDashboard } from "@/features/analytics/components/question-analytics-dashboard";
import { StudentAnalyticsDashboard } from "@/features/analytics/components/student-analytics-dashboard";
import { TopicAnalyticsDashboard } from "@/features/analytics/components/topic-analytics-dashboard";

const mockedGet = vi.mocked(apiGet);

const studentEmpty = {
  message: "Student analytics retrieved",
  data: {
    summary: {
      assessmentsAttempted: null,
      averageScore: null,
      accuracy: null,
      averageTime: null,
      lastCalculatedOn: null,
      freshness: "EMPTY",
    },
    recentAssessments: [],
    topics: [],
  },
};

const assessmentEmpty = {
  message: "Assessment analytics retrieved",
  data: {
    summary: {
      assessmentId: "a1",
      totalAttempts: null,
      averageScore: null,
      highestScore: null,
      lowestScore: null,
      lastCalculatedOn: null,
      freshness: "EMPTY",
    },
    sections: [],
  },
};

const questionEmpty = {
  message: "Question analytics retrieved",
  data: {
    questionId: "q1",
    totalAttempts: null,
    correctAttempts: null,
    incorrectAttempts: null,
    averageTime: null,
    difficultyIndex: null,
    correctPercentage: null,
    incorrectPercentage: null,
    skippedPercentage: null,
    lastCalculatedOn: null,
    freshness: "EMPTY",
  },
};

const topicEmpty = {
  message: "Topic analytics retrieved",
  data: {
    topicId: "t1",
    totalAttempts: null,
    averageScore: null,
    accuracy: null,
    sampleSize: 0,
    robustSample: false,
    lastCalculatedOn: null,
    freshness: "EMPTY",
  },
};

const organizationEmpty = {
  message: "Organization analytics retrieved",
  data: {
    organizationId: null,
    totalStudents: null,
    totalAssessments: null,
    totalAttempts: null,
    activeUsers: null,
    lastCalculatedOn: null,
    freshness: "EMPTY",
  },
};

interface DashboardCase {
  name: string;
  ui: () => ReactElement;
  payloads: Record<string, unknown>;
  loadingText: string;
  emptyText: string;
}

const cases: DashboardCase[] = [
  {
    name: "student",
    ui: () => <StudentAnalyticsDashboard />,
    payloads: { "/analytics/student": studentEmpty },
    loadingText: "Loading your analytics…",
    emptyText: "No evaluated attempts yet.",
  },
  {
    name: "assessment",
    ui: () => <AssessmentAnalyticsDashboard assessmentId="a1" />,
    payloads: {
      "/analytics/assessments/a1": assessmentEmpty,
      "/analytics/assessments/a1/questions": { message: "Assessment questions retrieved", data: [] },
    },
    loadingText: "Loading assessment analytics…",
    emptyText: "No assessment data for this period.",
  },
  {
    name: "question",
    ui: () => <QuestionAnalyticsDashboard questionId="q1" />,
    payloads: { "/analytics/questions/q1": questionEmpty },
    loadingText: "Loading question analytics…",
    emptyText: "No responses recorded for this question yet.",
  },
  {
    name: "topic",
    ui: () => <TopicAnalyticsDashboard topicId="t1" />,
    payloads: { "/analytics/topics/t1": topicEmpty },
    loadingText: "Loading topic analytics…",
    emptyText: "No topic mapping available.",
  },
  {
    name: "organization",
    ui: () => <OrganizationAnalyticsDashboard />,
    payloads: { "/analytics/organization": organizationEmpty },
    loadingText: "Loading organization analytics…",
    emptyText: "No organization analytics yet.",
  },
];

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe.each(cases)("$name analytics dashboard", ({ ui, payloads, loadingText, emptyText }) => {
  it("renders a loading state while the read is in flight", () => {
    mockedGet.mockImplementation(() => new Promise<never>(() => {}));

    renderWithClient(ui());

    expect(screen.getByRole("status")).toHaveTextContent(loadingText);
  });

  it("renders the empty state without fabricating zero metrics", async () => {
    mockedGet.mockImplementation((url) =>
      Promise.resolve((payloads[url] ?? { message: "ok", data: {} }) as never),
    );

    renderWithClient(ui());

    expect(await screen.findByText(emptyText)).toBeInTheDocument();
    expect(screen.queryByText("0%")).toBeNull();
    expect(screen.queryByText("0.00")).toBeNull();
  });

  it("renders an error state with a keyboard-operable retry", async () => {
    mockedGet.mockRejectedValue(
      new ApiClientError({
        message: "Analytics are temporarily unavailable",
        status: 503,
        code: "UNAVAILABLE",
      }),
    );

    renderWithClient(ui());

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Analytics are temporarily unavailable");
    expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
  });
});

describe("student analytics dashboard with data", () => {
  it("renders metrics, the score trend chart and its tabular fallback", async () => {
    mockedGet.mockResolvedValue({
      message: "Student analytics retrieved",
      data: {
        summary: {
          assessmentsAttempted: 2,
          averageScore: 61.25,
          accuracy: 72.5,
          averageTime: 34.2,
          lastCalculatedOn: "2026-10-20T10:00:00Z",
          freshness: "CURRENT",
        },
        recentAssessments: [
          {
            assessmentId: "a1",
            attemptId: "at1",
            assessmentVersionId: "v1",
            attemptMode: "EXAM",
            score: 60,
            percentage: 60,
            resultStatus: "PUBLISHED",
            startedOn: "2026-10-01T10:00:00Z",
          },
          {
            assessmentId: "a2",
            attemptId: "at2",
            assessmentVersionId: "v2",
            attemptMode: "EXAM",
            score: 62.5,
            percentage: 62.5,
            resultStatus: "PUBLISHED",
            startedOn: "2026-10-05T10:00:00Z",
          },
        ],
        topics: [
          {
            topicId: "11111111-1111-1111-1111-111111111111",
            evaluatedOccurrences: 12,
            accuracy: 75,
            averageScore: 3.5,
            robustSample: true,
            weakArea: false,
          },
          {
            topicId: "22222222-2222-2222-2222-222222222222",
            evaluatedOccurrences: 2,
            accuracy: 50,
            averageScore: 1.5,
            robustSample: false,
            weakArea: false,
          },
        ],
      },
    } as never);

    renderWithClient(<StudentAnalyticsDashboard />);

    const accuracy = await screen.findByRole("region", { name: "Accuracy" });
    expect(accuracy).toHaveTextContent("72.5%");

    expect(screen.getByRole("group", { name: /Score trend chart/ })).toBeInTheDocument();
    expect(screen.getByRole("table", { name: "Score trend: data table" })).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toHaveAttribute(
      "aria-valuetext",
      "1 of 2 topics with a reliable sample",
    );
    expect(screen.getAllByText("Up to date").length).toBeGreaterThan(0);
  });
});
