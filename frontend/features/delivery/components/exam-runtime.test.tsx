import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ExamRuntime } from "@/features/delivery/components/exam-runtime";

/**
 * End-to-end runtime behaviour ({@code specs/14-P5-Delivery-V1.5.md §73-§79, §130-§135}).
 *
 * <p>These tests drive the real component against a mocked transport to prove the acceptance
 * criterion "a student can complete an examination end-to-end in the UI": load, answer, navigate,
 * mark, clear, submit with confirmation, and see the terminal read-only state. They also prove the
 * safety properties — a failed save is never shown as saved, and no correctness ever reaches the DOM.
 */

const SERVER_TIME = "2026-10-22T10:00:00Z";
const DEADLINE = "2026-10-22T10:10:00Z";

function configuration(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    capabilities: {
      practice: false,
      allowClearResponse: true,
      allowMarkForReview: true,
      allowExplanation: false,
      allowHint: false,
    },
    navigation: {
      mode: "FREE",
      allowBackNavigation: true,
      allowSectionSwitch: true,
      allowQuestionReview: true,
    },
    timer: { mode: "GLOBAL", durationSeconds: 600, enforced: true },
    submission: { autoSubmitOnExpiry: false, allowManualSubmit: true },
    validation: { requireResponseWithinTemplate: false, maxResponseLength: 4000, autoSaveIntervalSeconds: 10 },
    presentation: {
      showPalette: true,
      showTimer: true,
      requiresLatexParsing: false,
      contentFormat: "MARKDOWN_LATEX_V1",
      sectionOrder: ["Quantitative Aptitude", "Reasoning"],
    },
    ...overrides,
  };
}

function attempt(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    attemptId: "attempt-1",
    assessmentVersionId: "version-1",
    scheduleId: "schedule-1",
    attemptMode: "EXAM",
    attemptStatus: "IN_PROGRESS",
    snapshotStatus: "READY",
    startedOn: SERVER_TIME,
    submittedOn: null,
    serverTime: SERVER_TIME,
    timer: { mode: "GLOBAL", durationSeconds: 600, deadline: DEADLINE },
    deliveryConfiguration: configuration(),
    progress: { totalQuestions: 2, answered: 0, visited: 0, markedForReview: 0 },
    ...overrides,
  };
}

/** Note the absence of any correctness field — the student view cannot carry one (§127). */
const QUESTIONS = [
  {
    attemptQuestionId: "aq-1",
    questionVersionId: "qv-1",
    displayOrder: 1,
    sectionName: "Quantitative Aptitude",
    marks: 2,
    negativeMarks: 0.5,
    requiresLatexParsing: false,
    contentFormat: "MARKDOWN_LATEX_V1",
    content: "What is 2 + 2?",
    renderedContent: "What is 2 + 2?",
    options: [
      { optionId: "o-1", optionKey: "A", displayOrder: 1, content: "3", renderedContent: "3" },
      { optionId: "o-2", optionKey: "B", displayOrder: 2, content: "4", renderedContent: "4" },
    ],
  },
  {
    attemptQuestionId: "aq-2",
    questionVersionId: "qv-2",
    displayOrder: 2,
    sectionName: "Reasoning",
    marks: 2,
    negativeMarks: 0.5,
    requiresLatexParsing: false,
    contentFormat: "MARKDOWN_LATEX_V1",
    content: "Which is larger?",
    renderedContent: "Which is larger?",
    options: [
      { optionId: "o-3", optionKey: "A", displayOrder: 1, content: "10", renderedContent: "10" },
      { optionId: "o-4", optionKey: "B", displayOrder: 2, content: "9", renderedContent: "9" },
    ],
  },
];

const mocks = vi.hoisted(() => ({
  getAttempt: vi.fn(),
  getAttemptQuestions: vi.fn(),
  saveResponse: vi.fn(),
  submitAttempt: vi.fn(),
  getResponse: vi.fn(),
}));

vi.mock("@/features/delivery/api/delivery-api", () => ({
  getAttempt: mocks.getAttempt,
  getAttemptQuestions: mocks.getAttemptQuestions,
  saveResponse: mocks.saveResponse,
  submitAttempt: mocks.submitAttempt,
  getResponse: mocks.getResponse,
  getActiveSession: vi.fn(),
  recoverSession: vi.fn(),
  startAssessment: vi.fn(),
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function renderRuntime() {
  return render(<ExamRuntime attemptId="attempt-1" />, { wrapper });
}

beforeEach(() => {
  vi.clearAllMocks();
  window.localStorage.clear();
  mocks.getAttempt.mockResolvedValue(attempt());
  mocks.getAttemptQuestions.mockResolvedValue(QUESTIONS);
  mocks.saveResponse.mockResolvedValue({
    view: {
      attemptId: "attempt-1",
      questionVersionId: "qv-1",
      response: "A",
      timeSpentSeconds: null,
      markedForReview: false,
      visited: true,
      etag: '"v1"',
    },
    etag: '"v1"',
  });
  mocks.submitAttempt.mockResolvedValue({
    attemptId: "attempt-1",
    status: "SUBMITTED",
    submittedOn: SERVER_TIME,
  });
});

describe("runtime loading and recovery", () => {
  it("shows a loading state, then the first question", async () => {
    renderRuntime();
    expect(screen.getByRole("status")).toHaveTextContent(/loading your examination/i);
    expect(await screen.findByTestId("question-content")).toHaveTextContent("What is 2 + 2?");
  });

  it("restores a reloaded attempt from the server rather than restarting it", async () => {
    renderRuntime();
    await screen.findByTestId("question-content");
    // The attempt id is the only client input; the caught/recovered session is announced (§134).
    expect(await screen.findByText(/session was recovered/i)).toBeInTheDocument();
  });

  it("surfaces a load failure without leaking internals", async () => {
    mocks.getAttempt.mockRejectedValue(new Error("SQLException in table attempt_response"));
    renderRuntime();
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/could not be loaded/i);
    expect(alert).not.toHaveTextContent(/SQLException|attempt_response/i);
  });
});

describe("answering and navigation", () => {
  it("saves the selected option and reflects it in the palette", async () => {
    renderRuntime();
    await screen.findByTestId("question-content");

    await userEvent.click(screen.getByRole("radio", { name: /B/ }));
    expect(screen.getByRole("radio", { name: /B/ })).toBeChecked();

    await waitFor(() => expect(mocks.saveResponse).toHaveBeenCalled());
    const [, questionVersionId, body] = mocks.saveResponse.mock.calls[0] ?? [];
    expect(questionVersionId).toBe("qv-1");
    expect(body).toMatchObject({ response: "B", visited: true });
    // The palette now reports the question as answered.
    expect(screen.getByRole("button", { name: "Question 1, Answered" })).toBeInTheDocument();
  });

  it("never sends an Extag-less write on the first save, then sends the ETag it received", async () => {
    renderRuntime();
    await screen.findByTestId("question-content");
    await userEvent.click(screen.getByRole("radio", { name: /A/ }));
    await waitFor(() => expect(mocks.saveResponse).toHaveBeenCalled());
    // First save has no If-Match because no ETag is known yet.
    expect(mocks.saveResponse.mock.calls[0]?.[3]).toBeUndefined();

    await userEvent.click(screen.getByRole("radio", { name: /B/ }));
    await waitFor(() => expect(mocks.saveResponse.mock.calls.length).toBeGreaterThan(1));
    // The second save carries the ETag returned by the first.
    expect(mocks.saveResponse.mock.calls.at(-1)?.[3]).toBe('"v1"');
  });

  it("marks a question for review and can unmark it", async () => {
    renderRuntime();
    await screen.findByTestId("question-content");

    await userEvent.click(screen.getByRole("button", { name: /mark for review/i }));
    expect(screen.getByRole("button", { name: /unmark for review/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Question 1, Marked for review" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /unmark for review/i }));
    expect(screen.getByRole("button", { name: /^mark for review$/i })).toBeInTheDocument();
  });

  it("clears a response without losing the visited state", async () => {
    renderRuntime();
    await screen.findByTestId("question-content");

    await userEvent.click(screen.getByRole("radio", { name: /A/ }));
    expect(screen.getByRole("button", { name: "Question 1, Answered" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /clear response/i }));
    expect(screen.getByRole("radio", { name: /A/ })).not.toBeChecked();
    // Visited but unanswered, not "not visited".
    expect(screen.getByRole("button", { name: "Question 1, Not answered" })).toBeInTheDocument();
  });

  it("navigates with next/previous and via the palette", async () => {
    renderRuntime();
    await screen.findByTestId("question-content");

    await userEvent.click(screen.getByRole("button", { name: /^next$/i }));
    expect(screen.getByTestId("question-content")).toHaveTextContent("Which is larger?");

    await userEvent.click(screen.getByRole("button", { name: /^previous$/i }));
    expect(screen.getByTestId("question-content")).toHaveTextContent("What is 2 + 2?");

    await userEvent.click(screen.getByRole("button", { name: /question 2,/i }));
    expect(screen.getByTestId("question-content")).toHaveTextContent("Which is larger?");
  });

  it("disables previous on the first question and next on the last", async () => {
    renderRuntime();
    await screen.findByTestId("question-content");
    expect(screen.getByRole("button", { name: /^previous$/i })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: /^next$/i }));
    expect(screen.getByRole("button", { name: /^next$/i })).toBeDisabled();
  });

  it("jumps to a section through the section navigator", async () => {
    renderRuntime();
    await screen.findByTestId("question-content");
    await userEvent.click(screen.getByRole("button", { name: /reasoning/i }));
    expect(screen.getByTestId("question-content")).toHaveTextContent("Which is larger?");
  });
});

describe("correct-answer protection", () => {
  it("renders no correctness information anywhere on the runtime", async () => {
    renderRuntime();
    await screen.findByTestId("question-content");
    const text = document.body.textContent?.toLowerCase() ?? "";
    expect(text).not.toContain("correct answer");
    expect(text).not.toContain("is the right answer");
    // The fixture has no `isCorrect`; a leaked one would fail schema parsing, not render.
    expect(text).not.toContain("iscorrect");
  });
});

describe("submission", () => {
  it("confirms with answered/unanswered counts before submitting", async () => {
    renderRuntime();
    await screen.findByTestId("question-content");

    await userEvent.click(screen.getByRole("button", { name: /submit examination/i }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText(/submit this examination/i)).toBeInTheDocument();
    // Both questions unanswered at this point.
    expect(dialog).toHaveTextContent(/2 unanswered/i);
    expect(mocks.submitAttempt).not.toHaveBeenCalled();
  });

  it("submits on confirmation and then becomes read-only", async () => {
    renderRuntime();
    await screen.findByTestId("question-content");

    await userEvent.click(screen.getByRole("button", { name: /submit examination/i }));
    await userEvent.click(screen.getByRole("button", { name: /^submit$/i }));

    await waitFor(() => expect(mocks.submitAttempt).toHaveBeenCalledWith("attempt-1"));
    expect(await screen.findByText(/has been submitted/i)).toBeInTheDocument();
    // No editing affordances remain (§133).
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /submit examination/i })).not.toBeInTheDocument();
  });

  it("lets the student cancel and keep working", async () => {
    renderRuntime();
    await screen.findByTestId("question-content");

    await userEvent.click(screen.getByRole("button", { name: /submit examination/i }));
    await userEvent.click(screen.getByRole("button", { name: /keep working/i }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(mocks.submitAttempt).not.toHaveBeenCalled();
    expect(screen.getByTestId("question-content")).toBeInTheDocument();
  });

  it("treats an already-submitted attempt as read-only on load", async () => {
    mocks.getAttempt.mockResolvedValue(attempt({ attemptStatus: "SUBMITTED", submittedOn: SERVER_TIME }));
    renderRuntime();
    expect(await screen.findByText(/has been submitted/i)).toBeInTheDocument();
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
  });

  it("reports a submission failure and keeps the exam editable", async () => {
    mocks.submitAttempt.mockRejectedValue(new Error("conflict"));
    renderRuntime();
    await screen.findByTestId("question-content");

    await userEvent.click(screen.getByRole("button", { name: /submit examination/i }));
    await userEvent.click(screen.getByRole("button", { name: /^submit$/i }));

    expect(await screen.findByText(/submission failed/i)).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /A/ })).toBeInTheDocument();
  });
});

describe("save failure is never shown as saved", () => {
  it("reports a failed save instead of a saved state", async () => {
    mocks.saveResponse.mockRejectedValue(new Error("network down"));
    renderRuntime();
    await screen.findByTestId("question-content");

    await userEvent.click(screen.getByRole("radio", { name: /A/ }));
    await waitFor(() => expect(mocks.saveResponse).toHaveBeenCalled());

    // After the save is rejected the attempt exhausts its retry budget and the queue reports a
    // failure. Under no circumstances may the indicator claim the answer is saved (§78).
    await waitFor(
      () => {
        const statuses = screen.getAllByRole("status").map((node) => node.textContent ?? "");
        expect(statuses.join(" ")).toMatch(/not saved|could not be sent|saving/i);
      },
      { timeout: 10_000 },
    );
    const bodyText = document.body.textContent ?? "";
    expect(bodyText).not.toMatch(/Saved \d/);
    expect(bodyText).not.toMatch(/All answers saved/);
  }, 15_000);
});

describe("configuration-driven rendering", () => {
  it("hides the palette and timer when the configuration says so", async () => {
    mocks.getAttempt.mockResolvedValue(
      attempt({
        deliveryConfiguration: configuration({
          presentation: {
            showPalette: false,
            showTimer: false,
            requiresLatexParsing: false,
            contentFormat: "MARKDOWN_LATEX_V1",
            sectionOrder: [],
          },
        }),
      }),
    );
    renderRuntime();
    await screen.findByTestId("question-content");
    expect(screen.queryByRole("timer")).not.toBeInTheDocument();
    expect(screen.queryByText(/question palette/i)).not.toBeInTheDocument();
  });

  it("hides mark-for-review and clear-response when the capabilities withhold them", async () => {
    mocks.getAttempt.mockResolvedValue(
      attempt({
        deliveryConfiguration: configuration({
          capabilities: {
            practice: false,
            allowClearResponse: false,
            allowMarkForReview: false,
            allowExplanation: false,
            allowHint: false,
          },
        }),
      }),
    );
    renderRuntime();
    await screen.findByTestId("question-content");
    expect(screen.queryByRole("button", { name: /mark for review/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /clear response/i })).not.toBeInTheDocument();
  });

  it("does not branch on an exam name — the practice label comes from configuration", async () => {
    mocks.getAttempt.mockResolvedValue(
      attempt({
        deliveryConfiguration: configuration({
          capabilities: {
            practice: true,
            allowClearResponse: true,
            allowMarkForReview: true,
            allowExplanation: false,
            allowHint: false,
          },
        }),
      }),
    );
    renderRuntime();
    await screen.findByTestId("question-content");
    expect(screen.getByRole("button", { name: /finish practice/i })).toBeInTheDocument();
  });
});
