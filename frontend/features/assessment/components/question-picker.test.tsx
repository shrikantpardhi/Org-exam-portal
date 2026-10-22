import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { QuestionPicker, defaultPublishedVersion } from "@/features/assessment/components/question-picker";
import { apiGet } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

const apiGetMock = vi.mocked(apiGet);

/**
 * Question picker ({@code specs/13-P4-Assessment-V1.5.md §25, §35, §101}).
 *
 * <p>The picker must search through the server with pagination, resolve the exact QuestionVersion,
 * and refuse to add an unpublished version.
 */
const questions = [
  {
    id: "q-1",
    templateId: "t-1",
    examId: "e-1",
    subjectId: "s-1",
    difficulty: "MEDIUM" as const,
    latestVersion: 2,
    latestReviewStatus: "APPROVED",
    published: true,
    updatedOn: "2026-01-01T00:00:00Z",
  },
];

function renderPicker(onAdd = vi.fn()) {
  render(
    <QueryClientProvider
      client={new QueryClient({
        defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
      })}
    >
      <QuestionPicker onAdd={onAdd} />
    </QueryClientProvider>,
  );
  return onAdd;
}

describe("QuestionPicker", () => {
  beforeEach(() => {
    apiGetMock.mockReset();
    apiGetMock.mockImplementation((url: string) => {
      if (url.startsWith("/questions/")) {
        return Promise.resolve({
          data: [
            {
              id: "qv-1",
              version: 1,
              reviewStatus: "APPROVED",
              published: false,
              marks: 1,
              negativeMarks: 0,
              difficulty: "EASY",
              publishedOn: null,
            },
            {
              id: "qv-2",
              version: 2,
              reviewStatus: "APPROVED",
              published: true,
              marks: 2,
              negativeMarks: 0.66,
              difficulty: "MEDIUM",
              publishedOn: "2026-02-01T00:00:00Z",
            },
          ],
          message: "ok",
        });
      }
      if (url.startsWith("/questions")) {
        return Promise.resolve({ data: questions, message: "ok" });
      }
      return Promise.resolve({ data: [], message: "ok" });
    });
  });

  it("requests the frozen question search path with pagination and filters", async () => {
    renderPicker();

    await waitFor(() => expect(apiGetMock).toHaveBeenCalled());

    const firstSearch = apiGetMock.mock.calls.find(([url]) => url.startsWith("/questions?"));
    expect(firstSearch).toBeDefined();
    const firstUrl = new URLSearchParams(firstSearch![0].split("?")[1]);
    expect(firstUrl.get("page")).toBe("0");
    expect(firstUrl.get("size")).toBe("10");

    await userEvent.type(screen.getByLabelText("Search"), "algebra");
    await userEvent.click(screen.getByRole("button", { name: "Search" }));

    await waitFor(() => {
      const filtered = apiGetMock.mock.calls
        .filter(([url]) => url.startsWith("/questions?"))
        .at(-1);
      const params = new URLSearchParams(filtered![0].split("?")[1]);
      expect(params.get("search")).toBe("algebra");
      expect(params.get("page")).toBe("0");
      expect(params.get("size")).toBe("10");
    });
  });

  it("loads versions from the per-question path when a question is selected", async () => {
    renderPicker();

    await waitFor(() => expect(screen.getByText("q-1")).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: "Select" }));

    await waitFor(() =>
      expect(apiGetMock.mock.calls.some(([url]) => url === "/questions/q-1/versions")).toBe(true),
    );
  });

  it("defaults to the newest published version and adds only that version", async () => {
    const onAdd = renderPicker();

    await waitFor(() => expect(screen.getByText("q-1")).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: "Select" }));

    // The unpublished v1 is not selectable; v2 is preselected and enabled.
    await waitFor(() => expect(screen.getByLabelText("Version 1")).toBeDisabled());
    expect(screen.getByLabelText("Version 2")).toBeChecked();

    const addButton = screen.getByRole("button", { name: "Add" });
    expect(addButton).toBeEnabled();

    await userEvent.click(addButton);

    expect(onAdd).toHaveBeenCalledWith({
      questionId: "q-1",
      questionVersionId: "qv-2",
      marks: 2,
      negativeMarks: 0.66,
    });
  });

  it("selects the highest published version regardless of list order", () => {
    const newest = defaultPublishedVersion([
      { id: "a", version: 1, reviewStatus: "APPROVED", published: true, marks: 1, negativeMarks: 0, difficulty: "EASY", publishedOn: null },
      { id: "c", version: 5, reviewStatus: "APPROVED", published: true, marks: 1, negativeMarks: 0, difficulty: "EASY", publishedOn: null },
      { id: "b", version: 3, reviewStatus: "APPROVED", published: true, marks: 1, negativeMarks: 0, difficulty: "EASY", publishedOn: null },
    ]);

    expect(newest?.id).toBe("c");
  });

  it("returns null when no version is published", () => {
    expect(
      defaultPublishedVersion([
        { id: "a", version: 1, reviewStatus: "APPROVED", published: false, marks: 1, negativeMarks: 0, difficulty: "EASY", publishedOn: null },
      ]),
    ).toBeNull();
  });
});
