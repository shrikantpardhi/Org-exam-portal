import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Component tests for the content authoring UI ({@code specs/12-P3-Content-V1.5.md §102-§110}).
 *
 * <p>Assertions target rendered output and the exact request payloads handed to the API layer, so a
 * regression in what the author sees or in what the backend receives fails here.
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

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  useParams: () => ({ id: "question-1" }),
}));

const auth = vi.hoisted(() => ({
  permissions: [
    "QUESTION_VIEW",
    "QUESTION_CREATE",
    "QUESTION_UPDATE",
    "QUESTION_SUBMIT_REVIEW",
    "QUESTION_REVIEW",
    "QUESTION_PUBLISH",
  ] as string[],
}));

vi.mock("@/lib/auth/auth-context", () => ({
  useAuth: () => ({
    can: (permission: string) => auth.permissions.includes(permission),
    status: "authenticated",
    permissions: auth.permissions,
  }),
}));

import {
  listChapters,
  listExams,
  listLanguages,
  listSubjects,
  listSubTopics,
  listTags,
  listTopics,
} from "@/features/academic/api/academic-api";
import {
  addOption,
  createQuestion,
  getExplanation,
  getHint,
  getQuestion,
  listAssets,
  listOptions,
  listPreviousExams,
  listQuestionTemplates,
  listVersions,
} from "@/features/content/api/content-api";
import { QuestionEditor } from "@/features/content/components/question-editor";
import { QuestionWorkspace } from "@/features/content/components/question-workspace";
import { OptionsEditor } from "@/features/content/components/options-editor";
import { VersionHistory } from "@/features/content/components/version-history";
import { createQueryClient } from "@/lib/query-client";
import type { OptionDraft, VersionListItem } from "@/features/content/schemas/content";

const exam = { id: "exam-1", code: "EXAM_A", name: "Exam Alpha", description: null, status: "ACTIVE" as const };
const otherExam = { id: "exam-2", code: "EXAM_B", name: "Exam Beta", description: null, status: "ACTIVE" as const };
const subjectsForExamOne = [
  { id: "subject-1", examId: "exam-1", code: "S1", name: "Subject One", description: null, status: "ACTIVE" as const },
];
const chapters = [
  { id: "chapter-1", subjectId: "subject-1", code: "C1", name: "Chapter One", description: null, status: "ACTIVE" as const },
];
const topics = [
  { id: "topic-1", chapterId: "chapter-1", code: "T1", name: "Topic One", description: null, status: "ACTIVE" as const },
];
const languages = [
  { id: "lang-1", code: "EN", name: "English", description: null, status: "ACTIVE" as const },
];
const tags = [
  { id: "tag-1", code: "TAG_A", name: "Algebra", description: null, status: "ACTIVE" as const },
  { id: "tag-2", code: "TAG_B", name: "Geometry", description: null, status: "ACTIVE" as const },
];

function renderWithClient(ui: React.ReactElement) {
  return render(<QueryClientProvider client={createQueryClient()}>{ui}</QueryClientProvider>);
}

const versionRows: VersionListItem[] = [
  {
    id: "version-1",
    version: 1,
    reviewStatus: "PUBLISHED",
    published: true,
    marks: 2,
    negativeMarks: 0.5,
    difficulty: "MEDIUM",
    publishedOn: "2024-01-02T10:00:00Z",
  },
  {
    id: "version-2",
    version: 2,
    reviewStatus: "DRAFT",
    published: false,
    marks: 3,
    negativeMarks: 1,
    difficulty: "HARD",
    publishedOn: null,
  },
];

const templates = [
  { id: "template-1", code: "SINGLE_CHOICE", name: "Single choice", description: null, requiresLatexParsing: true, status: "ACTIVE" as const },
];

const question = {
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
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listExams).mockResolvedValue([exam, otherExam]);
  vi.mocked(listSubjects).mockResolvedValue(subjectsForExamOne);
  vi.mocked(listChapters).mockResolvedValue(chapters);
  vi.mocked(listTopics).mockResolvedValue(topics);
  vi.mocked(listSubTopics).mockResolvedValue([]);
  vi.mocked(listLanguages).mockResolvedValue(languages);
  vi.mocked(listTags).mockResolvedValue(tags);
  vi.mocked(listQuestionTemplates).mockResolvedValue(templates);
  vi.mocked(getQuestion).mockResolvedValue(question);
  vi.mocked(listOptions).mockResolvedValue([]);
  vi.mocked(listAssets).mockResolvedValue([]);
  vi.mocked(listPreviousExams).mockResolvedValue([]);
  vi.mocked(getExplanation).mockRejectedValue({ status: 404 });
  vi.mocked(getHint).mockRejectedValue({ status: 404 });
  vi.mocked(listVersions).mockResolvedValue(versionRows);
});

describe("QuestionEditor", () => {
  it("loads subjects for the chosen exam and sends the exact create payload", async () => {
    vi.mocked(createQuestion).mockResolvedValue({
      id: "question-created",
      templateId: "template-1",
      examId: "exam-1",
      subjectId: "subject-1",
      chapterId: null,
      topicId: null,
      subTopicId: null,
      status: "ACTIVE",
      versions: [
        {
          id: "version-1",
          version: 1,
          reviewStatus: "DRAFT",
          published: false,
          marks: 1,
          negativeMarks: 0,
          difficulty: "MEDIUM",
          publishedOn: null,
        },
      ],
      tagIds: [],
    });

    const user = userEvent.setup();
    renderWithClient(<QuestionEditor />);

    // Templates come from the content API; this test focuses on the taxonomy and payload.
    await screen.findByRole("option", { name: "Single choice" });
    await user.selectOptions(screen.getByLabelText("Template"), "template-1");
    await screen.findByRole("option", { name: "Exam Alpha" });
    await user.selectOptions(screen.getByLabelText("Exam"), "exam-1");
    expect(await screen.findByLabelText("Subject")).toBeEnabled();
    expect(listSubjects).toHaveBeenCalledWith("exam-1");

    await user.selectOptions(screen.getByLabelText("Subject"), "subject-1");
    await screen.findByRole("option", { name: "Chapter One" });
    await user.selectOptions(screen.getByLabelText("Chapter"), "chapter-1");
    await screen.findByRole("option", { name: "Topic One" });
    await user.selectOptions(screen.getByLabelText("Topic"), "topic-1");
    await screen.findByRole("option", { name: "English" });
    await user.selectOptions(screen.getByLabelText("Language"), "lang-1");
    await user.type(screen.getByLabelText("Content"), "What is \\(2+2\\)?");
    await user.clear(screen.getByLabelText("Marks"));
    await user.type(screen.getByLabelText("Marks"), "2");

    await user.click(screen.getByRole("button", { name: "Create question" }));

    expect(createQuestion).toHaveBeenCalledTimes(1);
    const payload = vi.mocked(createQuestion).mock.calls[0]?.[0];
    expect(payload).toMatchObject({
      examId: "exam-1",
      subjectId: "subject-1",
      chapterId: "chapter-1",
      topicId: "topic-1",
      languageId: "lang-1",
      content: "What is \\(2+2\\)?",
      marks: 2,
      difficulty: "MEDIUM",
    });
    expect(Object.keys(payload ?? {})).not.toContain("options");
    expect(Object.keys(payload ?? {})).not.toContain("tagIds");
    expect(Object.keys(payload ?? {})).not.toContain("subTopicId");
  });

  it("clears the stale subject, chapter and topic when the exam changes", async () => {
    const user = userEvent.setup();
    renderWithClient(<QuestionEditor />);

    await screen.findByRole("option", { name: "Exam Alpha" });

    await user.selectOptions(screen.getByLabelText("Exam"), "exam-1");
    await screen.findByRole("option", { name: "Subject One" });
    await user.selectOptions(screen.getByLabelText("Subject"), "subject-1");
    await screen.findByRole("option", { name: "Chapter One" });
    await user.selectOptions(screen.getByLabelText("Chapter"), "chapter-1");
    await screen.findByRole("option", { name: "Topic One" });
    await user.selectOptions(screen.getByLabelText("Topic"), "topic-1");

    expect(screen.getByLabelText("Subject")).toHaveValue("subject-1");
    expect(screen.getByLabelText("Chapter")).toHaveValue("chapter-1");
    expect(screen.getByLabelText("Topic")).toHaveValue("topic-1");

    await user.selectOptions(screen.getByLabelText("Exam"), "exam-2");

    expect(screen.getByLabelText("Subject")).toHaveValue("");
    expect(screen.getByLabelText("Chapter")).toHaveValue("");
    expect(screen.getByLabelText("Topic")).toHaveValue("");
    expect(screen.getByLabelText("Chapter")).toBeDisabled();
    expect(listSubjects).toHaveBeenLastCalledWith("exam-2");
  });

  it("saving the question posts filled options against the created version", async () => {
    vi.mocked(createQuestion).mockResolvedValue({
      id: "question-created",
      templateId: "template-1",
      examId: "exam-1",
      subjectId: "subject-1",
      chapterId: null,
      topicId: null,
      subTopicId: null,
      status: "ACTIVE",
      versions: [
        {
          id: "version-created",
          version: 1,
          reviewStatus: "DRAFT",
          published: false,
          marks: 1,
          negativeMarks: 0,
          difficulty: "MEDIUM",
          publishedOn: null,
        },
      ],
      tagIds: [],
    });
    vi.mocked(addOption).mockResolvedValue({
      id: "option-1",
      questionVersionId: "version-created",
      optionKey: "A",
      content: "4",
      displayOrder: 0,
      isCorrect: true,
    });

    const user = userEvent.setup();
    renderWithClient(<QuestionEditor />);

    await screen.findByRole("option", { name: "Single choice" });
    await user.selectOptions(screen.getByLabelText("Template"), "template-1");
    await screen.findByRole("option", { name: "Exam Alpha" });

    await user.selectOptions(screen.getByLabelText("Exam"), "exam-1");
    await screen.findByRole("option", { name: "Subject One" });
    await user.selectOptions(screen.getByLabelText("Subject"), "subject-1");
    await screen.findByRole("option", { name: "English" });
    await user.selectOptions(screen.getByLabelText("Language"), "lang-1");
    await user.type(screen.getByLabelText("Content"), "What is 2+2?");

    await user.type(screen.getByLabelText("Option 1 content"), "4");
    await user.type(screen.getByLabelText("Option 2 content"), "5");

    await user.click(screen.getByRole("button", { name: "Create question" }));

    expect(addOption).toHaveBeenCalledWith(
      "question-created",
      "version-created",
      expect.objectContaining({ optionKey: "A", content: "4", displayOrder: 0, isCorrect: true }),
    );
    expect(addOption).toHaveBeenCalledWith(
      "question-created",
      "version-created",
      expect.objectContaining({ optionKey: "B", content: "5", displayOrder: 1, isCorrect: false }),
    );
  });
});

describe("OptionsEditor", () => {
  const initialRows: OptionDraft[] = [
    { optionKey: "A", content: "one", displayOrder: 0, isCorrect: true },
    { optionKey: "B", content: "two", displayOrder: 1, isCorrect: false },
    { optionKey: "C", content: "three", displayOrder: 2, isCorrect: false },
  ];

  function Harness() {
    const [rows, setRows] = useState<OptionDraft[]>(initialRows);
    return <OptionsEditor rows={rows} onChange={setRows} answerModel="SINGLE_OPTION" />;
  }

  it("adds, removes and reorders options", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(screen.getByRole("button", { name: "Add option" }));
    expect(screen.getByLabelText("Option 4 key")).toHaveValue("D");

    await user.click(screen.getByRole("button", { name: "Remove option 4" }));
    expect(screen.queryByLabelText("Option 4 key")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Move option 2 up" }));
    expect(screen.getByLabelText("Option 1 key")).toHaveValue("B");
    expect(screen.getByLabelText("Option 2 key")).toHaveValue("A");

    const rows = screen.getByRole("list", { name: "Option rows" });
    expect(within(rows).getAllByRole("listitem")).toHaveLength(3);
  });

  it("keeps exactly one correct option in single-correct mode", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    expect(screen.getByLabelText("Option 1 correct")).toBeChecked();
    expect(screen.getByLabelText("Option 3 correct")).not.toBeChecked();

    await user.click(screen.getByLabelText("Option 3 correct"));

    expect(screen.getByLabelText("Option 3 correct")).toBeChecked();
    expect(screen.getByLabelText("Option 1 correct")).not.toBeChecked();
    expect(screen.getAllByRole("radio", { checked: true })).toHaveLength(1);
  });
});

describe("VersionHistory", () => {
  it("renders version facts from the server and offers a new version for a published one", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    const onNewVersion = vi.fn();

    render(
      <VersionHistory
        versions={versionRows}
        onSelect={onSelect}
        onCreateNewVersion={onNewVersion}
      />,
    );

    const table = within(screen.getByRole("table", { name: "Versions" }));
    expect(table.getByText("v1")).toBeInTheDocument();
    expect(table.getByText("v2")).toBeInTheDocument();
    expect(table.getAllByText("Published").length).toBeGreaterThan(0);
    expect(table.getByText("Draft")).toBeInTheDocument();
    expect(table.getByText("HARD")).toBeInTheDocument();
    expect(table.getByText("2024-01-02T10:00:00Z")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Create a new version from version 1" }));
    expect(onNewVersion).toHaveBeenCalledWith("version-1");

    await user.click(screen.getByRole("button", { name: "Select version 2" }));
    expect(onSelect).toHaveBeenCalledWith("version-2");
  });

  it("explains that a selected published version is immutable", () => {
    render(
      <VersionHistory
        versions={versionRows}
        selectedVersionId="version-1"
        onSelect={vi.fn()}
        onCreateNewVersion={vi.fn()}
      />,
    );

    expect(screen.getAllByRole("note")[0]).toHaveTextContent("published and therefore immutable");
    expect(screen.queryByRole("button", { name: "Select version 1" })).toBeInTheDocument();
  });
});

describe("QuestionWorkspace", () => {
  it("renders version-bound panels and disables explanation and hint for a published version", async () => {
    vi.mocked(listVersions).mockResolvedValue([versionRows[0]!]);

    renderWithClient(<QuestionWorkspace questionId="question-1" />);

    const explanation = await screen.findByRole("region", { name: "Explanation" });
    expect(within(explanation).getByLabelText("Explanation")).toBeDisabled();
    expect(within(explanation).getByRole("button", { name: "Save explanation" })).toBeDisabled();
    expect(within(explanation).getByRole("note")).toHaveTextContent("cannot be changed");

    const hint = screen.getByRole("region", { name: "Hint" });
    expect(within(hint).getByLabelText("Hint")).toBeDisabled();
    expect(within(hint).getByRole("button", { name: "Save hint" })).toBeDisabled();
  });

  it("enabled explanation and hint editing for a draft version", async () => {
    vi.mocked(listVersions).mockResolvedValue([versionRows[1]!]);

    renderWithClient(<QuestionWorkspace questionId="question-1" />);

    const explanation = await screen.findByRole("region", { name: "Explanation" });
    await waitFor(() =>
      expect(within(explanation).getByLabelText("Explanation")).toBeEnabled(),
    );
    expect(within(explanation).getByRole("button", { name: "Save explanation" })).toBeEnabled();
    expect(within(explanation).queryByRole("note")).not.toBeInTheDocument();
  });
});




