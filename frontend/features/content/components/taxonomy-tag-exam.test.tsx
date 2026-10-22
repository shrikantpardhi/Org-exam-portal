import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Tag and previous-exam authoring tests ({@code specs/12-P3-Content-V1.5.md §108, §109}).
 *
 * <p>Both editors must be driven by the Academic masters: a tag is only ever associated by id, and
 * a previous exam is submitted as {@code examId}, never as a typed exam name.
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
  useAuth: () => ({ can: () => true, status: "authenticated", permissions: [] }),
}));

import { listExams, listTags } from "@/features/academic/api/academic-api";
import {
  addPreviousExam,
  deletePreviousExam,
  getExplanation,
  getHint,
  getQuestion,
  listAssets,
  listOptions,
  listPreviousExams,
  listVersions,
  updatePreviousExam,
  updateQuestion,
} from "@/features/content/api/content-api";
import { PreviousExamEditor } from "@/features/content/components/previous-exam-editor";
import { QuestionWorkspace } from "@/features/content/components/question-workspace";
import { TagMultiSelect } from "@/features/content/components/tag-multi-select";
import { createQueryClient } from "@/lib/query-client";
import { useState } from "react";

const exams = [
  { id: "exam-1", code: "EXAM_A", name: "Exam Alpha", description: null, status: "ACTIVE" as const },
  { id: "exam-2", code: "EXAM_B", name: "Exam Beta", description: null, status: "ACTIVE" as const },
];

const tags = [
  { id: "tag-1", code: "TAG_A", name: "Algebra", description: null, status: "ACTIVE" as const },
  { id: "tag-2", code: "TAG_B", name: "Geometry", description: null, status: "ACTIVE" as const },
];

const history = [
  { id: "history-1", questionId: "question-1", examId: "exam-1", year: 2023, shift: "Morning", source: "Prelims" },
];

function renderWithClient(ui: React.ReactElement) {
  return render(<QueryClientProvider client={createQueryClient()}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listExams).mockResolvedValue(exams);
  vi.mocked(listTags).mockResolvedValue(tags);
  vi.mocked(listPreviousExams).mockResolvedValue(history);
  vi.mocked(listVersions).mockResolvedValue([]);
  vi.mocked(listOptions).mockResolvedValue([]);
  vi.mocked(listAssets).mockResolvedValue([]);
  vi.mocked(getExplanation).mockRejectedValue({ status: 404 });
  vi.mocked(getHint).mockRejectedValue({ status: 404 });
});

describe("TagMultiSelect", () => {
  function Harness() {
    const [selected, setSelected] = useState<string[]>(["tag-1"]);
    return <TagMultiSelect selected={selected} onChange={setSelected} />;
  }

  it("adds a tag from the catalog and removes an associated one", async () => {
    const user = userEvent.setup();
    renderWithClient(<Harness />);

    const selectedList = await screen.findByRole("list", { name: "Selected tags" });
    await waitFor(() => expect(within(selectedList).getByText("Algebra")).toBeInTheDocument());

    await user.click(await screen.findByRole("button", { name: "Geometry" }));

    expect(within(screen.getByRole("list", { name: "Selected tags" })).getByText("Geometry")).toBeInTheDocument();
    expect(within(screen.getByRole("list", { name: "Selected tags" })).getByText("Algebra")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Remove tag Algebra" }));

    const remaining = screen.getByRole("list", { name: "Selected tags" });
    expect(within(remaining).queryByText("Algebra")).not.toBeInTheDocument();
    expect(within(remaining).getByText("Geometry")).toBeInTheDocument();
  });

  it("searches the tag catalog and never offers to create a tag", async () => {
    const user = userEvent.setup();
    renderWithClient(<Harness />);

    await screen.findByRole("list", { name: "Selected tags" });
    await user.type(screen.getByLabelText("Search tags"), "Geom");

    await waitFor(() => expect(listTags).toHaveBeenLastCalledWith({ search: "Geom" }));
    expect(screen.queryByRole("button", { name: /create/i })).not.toBeInTheDocument();
  });

  it("saves the association as a tagIds payload on the question update", async () => {
    vi.mocked(listVersions).mockResolvedValue([]);
    vi.mocked(getQuestion).mockResolvedValue({
      id: "question-1",
      templateId: "template-1",
      examId: "exam-1",
      subjectId: "subject-1",
      chapterId: null,
      topicId: null,
      subTopicId: null,
      status: "ACTIVE",
      versions: [],
      tagIds: ["tag-1"],
    });
    vi.mocked(updateQuestion).mockResolvedValue({
      id: "question-1",
      templateId: "template-1",
      examId: "exam-1",
      subjectId: "subject-1",
      chapterId: null,
      topicId: null,
      subTopicId: null,
      status: "ACTIVE",
      versions: [],
      tagIds: ["tag-1", "tag-2"],
    });

    const user = userEvent.setup();
    renderWithClient(<QuestionWorkspace questionId="question-1" />);

    await screen.findByRole("list", { name: "Selected tags" });
    await user.click(await screen.findByRole("button", { name: "Geometry" }));
    await user.click(screen.getByRole("button", { name: "Save tags" }));

    await waitFor(() => expect(updateQuestion).toHaveBeenCalledTimes(1));
    const [questionId, payload] = vi.mocked(updateQuestion).mock.calls[0] ?? [];
    expect(questionId).toBe("question-1");
    expect(payload?.tagIds).toEqual(["tag-1", "tag-2"]);
    expect(payload).not.toHaveProperty("options");
    expect(payload).not.toHaveProperty("content");
  });
});

describe("PreviousExamEditor", () => {
  it("lists history from the masters and submits examId, never a typed exam name", async () => {
    const user = userEvent.setup();
    vi.mocked(addPreviousExam).mockResolvedValue({
      id: "history-2",
      questionId: "question-1",
      examId: "exam-2",
      year: 2022,
      shift: "Evening",
      source: "Mains",
    });

    renderWithClient(<PreviousExamEditor questionId="question-1" />);

    const table = await screen.findByRole("table", { name: "Previous exams" });
    expect(within(table).getByText("Exam Alpha")).toBeInTheDocument();
    expect(within(table).getByText("2023")).toBeInTheDocument();
    expect(within(table).getByText("Morning")).toBeInTheDocument();
    expect(within(table).getByText("Prelims")).toBeInTheDocument();

    // The exam field is a select over the masters, not a free-text name input.
    const examSelect = screen.getByLabelText("Previous exam name");
    expect(examSelect.tagName).toBe("SELECT");
    await user.selectOptions(examSelect, "exam-2");
    await user.type(screen.getByLabelText("Previous exam year"), "2022");
    await user.type(screen.getByLabelText("Previous exam shift"), "Evening");
    await user.type(screen.getByLabelText("Previous exam source"), "Mains");
    await user.click(screen.getByRole("button", { name: "Add previous exam" }));

    expect(addPreviousExam).toHaveBeenCalledWith("question-1", {
      examId: "exam-2",
      year: 2022,
      shift: "Evening",
      source: "Mains",
    });
    expect(vi.mocked(addPreviousExam).mock.calls[0]?.[1]).not.toHaveProperty("examName");
  });

  it("edits and removes an existing history row", async () => {
    const user = userEvent.setup();
    vi.mocked(updatePreviousExam).mockResolvedValue({
      id: "history-1",
      questionId: "question-1",
      examId: "exam-1",
      year: 2021,
      shift: "Morning",
      source: "Prelims",
    });
    vi.mocked(deletePreviousExam).mockResolvedValue(undefined);

    renderWithClient(<PreviousExamEditor questionId="question-1" />);

    await user.click(await screen.findByRole("button", { name: "Edit previous exam Exam Alpha" }));
    const yearInput = screen.getByLabelText("Year for Exam Alpha");
    await user.clear(yearInput);
    await user.type(yearInput, "2021");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(updatePreviousExam).toHaveBeenCalledWith("question-1", "history-1", {
      year: 2021,
      shift: "Morning",
      source: "Prelims",
    });
    expect(vi.mocked(updatePreviousExam).mock.calls[0]?.[2]).not.toHaveProperty("examId");

    await user.click(screen.getByRole("button", { name: "Remove previous exam Exam Alpha" }));
    expect(deletePreviousExam).toHaveBeenCalledWith("question-1", "history-1");
  });

  it("reports an empty history instead of rendering a blank table", async () => {
    vi.mocked(listPreviousExams).mockResolvedValue([]);

    renderWithClient(<PreviousExamEditor questionId="question-1" />);

    expect(await screen.findByText("No previous exam recorded.")).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: "Previous exams" })).not.toBeInTheDocument();
  });
});


