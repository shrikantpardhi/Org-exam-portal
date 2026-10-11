import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

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

import { listExams, listSubjects } from "@/features/academic/api/academic-api";
import { AcademicHierarchy } from "@/features/academic/components/academic-hierarchy";
import { createQueryClient } from "@/lib/query-client";

const mockedExams = vi.mocked(listExams);
const mockedSubjects = vi.mocked(listSubjects);

function renderHierarchy() {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <AcademicHierarchy />
    </QueryClientProvider>,
  );
}

describe("AcademicHierarchy", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedExams.mockResolvedValue([{ id: "e1", code: "SSC", name: "SSC CGL", status: "ACTIVE" }]);
    mockedSubjects.mockResolvedValue([
      { id: "s1", examId: "e1", code: "MATH", name: "Mathematics", status: "ACTIVE" },
    ]);
  });

  it("loads children lazily, only when a node is expanded", async () => {
    renderHierarchy();

    const examButton = await screen.findByRole("button", { name: /SSC CGL/ });
    expect(mockedExams).toHaveBeenCalledTimes(1);
    // No child request until the exam is expanded.
    expect(mockedSubjects).not.toHaveBeenCalled();

    await userEvent.click(examButton);

    expect(await screen.findByRole("button", { name: /Mathematics/ })).toBeInTheDocument();
    expect(mockedSubjects).toHaveBeenCalledWith("e1");
  });
});
