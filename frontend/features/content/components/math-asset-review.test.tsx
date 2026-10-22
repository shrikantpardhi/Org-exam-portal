import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Math and asset authoring controls ({@code specs/12-P3-Content-V1.5.md §104, §105, §111}).
 *
 * <p>The math buttons must wrap the live selection in the approved delimiters, and an asset
 * reference must always be the canonical shortcode — never a filesystem path.
 */

vi.mock("@/features/academic/api/academic-api", () => ({
  listExams: vi.fn(),
  listSubjects: vi.fn(),
  listChapters: vi.fn(),
  listTopics: vi.fn(),
  listSubTopics: vi.fn(),
  listLanguages: vi.fn(),
  listTags: vi.fn(),
  createExam: vi.fn(),
  createSubject: vi.fn(),
  createChapter: vi.fn(),
  createTopic: vi.fn(),
  createSubTopic: vi.fn(),
  createLanguage: vi.fn(),
  createTag: vi.fn(),
  updateLanguage: vi.fn(),
  updateTag: vi.fn(),
  archiveExam: vi.fn(),
  archiveLanguage: vi.fn(),
  archiveTag: vi.fn(),
}));

vi.mock("@/features/content/api/content-api", () => ({
  listQuestionTemplates: vi.fn(),
  getQuestionTemplate: vi.fn(),
  createQuestionTemplate: vi.fn(),
  updateQuestionTemplate: vi.fn(),
  listQuestions: vi.fn(),
  getQuestion: vi.fn(),
  createQuestion: vi.fn(),
  updateQuestion: vi.fn(),
  listVersions: vi.fn(),
  createVersion: vi.fn(),
  getVersion: vi.fn(),
  listOptions: vi.fn(),
  submitReview: vi.fn(),
  approveVersion: vi.fn(),
  rejectVersion: vi.fn(),
  publishVersion: vi.fn(),
  addOption: vi.fn(),
  updateOption: vi.fn(),
  deleteOption: vi.fn(),
  listAssets: vi.fn(),
  uploadAsset: vi.fn(),
  deleteAsset: vi.fn(),
  getExplanation: vi.fn(),
  createExplanation: vi.fn(),
  updateExplanation: vi.fn(),
  getHint: vi.fn(),
  createHint: vi.fn(),
  updateHint: vi.fn(),
  listPreviousExams: vi.fn(),
  addPreviousExam: vi.fn(),
  updatePreviousExam: vi.fn(),
  deletePreviousExam: vi.fn(),
}));

vi.mock("@/lib/auth/auth-context", () => ({
  useAuth: () => ({
    can: () => true,
    status: "authenticated",
    permissions: ["QUESTION_VIEW", "QUESTION_REVIEW"],
  }),
}));

import { listAssets, listQuestions } from "@/features/content/api/content-api";
import { AssetPanel } from "@/features/content/components/asset-panel";
import { MathTextField } from "@/features/content/components/math-text-field";
import { ReviewQueue } from "@/features/content/components/review-queue";
import { createQueryClient } from "@/lib/query-client";
import { useState } from "react";

const ASSET_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

function renderWithClient(ui: React.ReactElement) {
  return render(<QueryClientProvider client={createQueryClient()}>{ui}</QueryClientProvider>);
}

describe("MathTextField", () => {
  function Harness({ initial = "" }: { initial?: string }) {
    const [value, setValue] = useState(initial);
    return <MathTextField label="Content" value={value} onChange={setValue} />;
  }

  it("wraps the selected text in inline delimiters without emitting dollar signs", async () => {
    const user = userEvent.setup();
    render(<Harness initial="Solve x+1" />);

    const textarea = screen.getByLabelText("Content") as HTMLTextAreaElement;
    textarea.setSelectionRange(6, 9);
    await user.click(screen.getByRole("button", { name: "Insert inline math" }));

    await waitFor(() => expect(textarea.value).toBe("Solve \\(x+1\\)"));
    expect(textarea.value).not.toContain("$");
  });

  it("wraps the selected text in block delimiters", async () => {
    const user = userEvent.setup();
    render(<Harness initial="Area = 4" />);

    const textarea = screen.getByLabelText("Content") as HTMLTextAreaElement;
    textarea.setSelectionRange(0, 8);
    await user.click(screen.getByRole("button", { name: "Insert block math" }));

    await waitFor(() => expect(textarea.value).toBe("\\[Area = 4\\]"));
  });

  it("leaves currency text alone and inserts a placeholder at the caret", async () => {
    const user = userEvent.setup();
    render(<Harness initial="A pen costs $500." />);

    const textarea = screen.getByLabelText("Content") as HTMLTextAreaElement;
    textarea.setSelectionRange(textarea.value.length, textarea.value.length);
    await user.click(screen.getByRole("button", { name: "Insert inline math" }));

    await waitFor(() => expect(textarea.value).toBe("A pen costs $500.\\(x\\)"));
  });
});

describe("AssetPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAssets).mockResolvedValue([
      {
        id: ASSET_ID,
        questionVersionId: "version-1",
        fileName: "diagram.png",
        fileType: "image/png",
        status: "ACTIVE",
      },
    ]);
  });

  it("inserts the canonical shortcode rather than a filesystem path", async () => {
    const user = userEvent.setup();
    const onInsert = vi.fn();

    renderWithClient(<AssetPanel versionId="version-1" published={false} onInsertReference={onInsert} />);

    await user.click(await screen.findByRole("button", { name: "Insert asset diagram.png" }));

    expect(onInsert).toHaveBeenCalledWith(`[asset: ${ASSET_ID}]`);
    expect(vi.mocked(onInsert).mock.calls[0]?.[0]).not.toContain("/");
  });

  it("disables asset mutation for a published version", async () => {
    renderWithClient(<AssetPanel versionId="version-1" published onInsertReference={vi.fn()} />);

    expect(await screen.findByLabelText("Upload asset")).toBeDisabled();
    expect(await screen.findByRole("button", { name: "Remove asset diagram.png" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Insert asset diagram.png" })).toBeDisabled();
  });

  it("states the missing preview route instead of inventing one", async () => {
    renderWithClient(<AssetPanel versionId="version-1" published={false} />);

    expect(await screen.findByRole("note")).toHaveTextContent("no content route");
  });
});

describe("ReviewQueue", () => {
  it("renders only the server-reported review state and links to the question", async () => {
    vi.mocked(listQuestions).mockResolvedValue([
      {
        id: "question-1",
        templateId: "template-1",
        examId: "exam-1",
        subjectId: "subject-1",
        difficulty: "HARD",
        latestVersion: 2,
        latestReviewStatus: "IN_REVIEW",
        published: false,
        updatedOn: null,
      },
      {
        id: "question-2",
        templateId: "template-1",
        examId: "exam-1",
        subjectId: "subject-1",
        difficulty: "EASY",
        latestVersion: 1,
        latestReviewStatus: "REJECTED",
        published: false,
        updatedOn: null,
      },
    ]);

    renderWithClient(<ReviewQueue />);

    const table = await screen.findByRole("table", { name: "Review queue" });
    expect(within(table).getByText("In review")).toBeInTheDocument();
    expect(within(table).getByText("Rejected")).toBeInTheDocument();
    expect(within(table).getByText("HARD")).toBeInTheDocument();
    expect(within(table).getAllByRole("link", { name: "Review this question" })).toHaveLength(2);
    // Nothing is labelled Approved because the server never said APPROVED.
    expect(screen.queryByText("Approved")).not.toBeInTheDocument();
  });

  it("reports an empty queue", async () => {
    vi.mocked(listQuestions).mockResolvedValue([]);

    renderWithClient(<ReviewQueue />);

    expect(await screen.findByText("Nothing is awaiting review.")).toBeInTheDocument();
  });
});


