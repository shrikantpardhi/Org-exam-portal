"use client";

import { useState } from "react";
import type { ReactNode } from "react";

import {
  useChapters,
  useExams,
  useSubTopics,
  useSubjects,
  useTopics,
} from "@/features/academic/hooks/use-academic";
import type { UseQueryResult } from "@tanstack/react-query";

/**
 * Hierarchical navigation ({@code specs/11-P2-Academic-V1.5.md §51, §59, §60-§64}).
 *
 * <p>Lazy by construction: a child branch component is mounted only when its parent is expanded, so
 * its query runs only then. The whole tree is never preloaded and the hierarchy is never recursively
 * fetched as one object graph ({@code §52, §132 Failure 5}).
 */
export function AcademicHierarchy() {
  const exams = useExams();

  if (exams.isPending) {
    return (
      <p role="status" className="text-sm text-muted-foreground">
        Loading exams…
      </p>
    );
  }
  if (exams.isError) {
    return (
      <p role="alert" className="text-sm text-red-600">
        {exams.error.message}
      </p>
    );
  }
  if (!exams.data || exams.data.length === 0) {
    return <p className="text-sm text-muted-foreground">No exams yet.</p>;
  }

  return (
    <ul aria-label="Academic hierarchy" className="space-y-1">
      {exams.data.map((exam) => (
        <Branch key={exam.id} label={exam.name} heading="Exam">
          <SubjectBranch examId={exam.id} />
        </Branch>
      ))}
    </ul>
  );
}

function Branch({ label, heading, children }: { label: string; heading: string; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  return (
    <li>
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="rounded px-1 text-left text-sm font-medium underline-offset-2 hover:underline"
      >
        <span className="text-muted-foreground">{heading}: </span>
        {label}
      </button>
      {open ? <div className="ml-4 border-l border-border pl-3">{children}</div> : null}
    </li>
  );
}

function BranchList<T>({
  query,
  render,
  emptyLabel,
}: {
  query: UseQueryResult<T[], Error>;
  render: (item: T) => ReactNode;
  emptyLabel: string;
}) {
  if (query.isPending) {
    return (
      <p role="status" className="text-sm text-muted-foreground">
        Loading…
      </p>
    );
  }
  if (query.isError) {
    return (
      <p role="alert" className="text-sm text-red-600">
        {query.error.message}
      </p>
    );
  }
  if (!query.data || query.data.length === 0) {
    return <p className="text-sm text-muted-foreground">{emptyLabel}</p>;
  }
  return <ul className="space-y-1">{query.data.map(render)}</ul>;
}

function SubjectBranch({ examId }: { examId: string }) {
  const subjects = useSubjects(examId);
  return (
    <BranchList
      query={subjects}
      emptyLabel="No subjects."
      render={(subject) => (
        <Branch key={subject.id} label={subject.name} heading="Subject">
          <ChapterBranch subjectId={subject.id} />
        </Branch>
      )}
    />
  );
}

function ChapterBranch({ subjectId }: { subjectId: string }) {
  const chapters = useChapters(subjectId);
  return (
    <BranchList
      query={chapters}
      emptyLabel="No chapters."
      render={(chapter) => (
        <Branch key={chapter.id} label={chapter.name} heading="Chapter">
          <TopicBranch chapterId={chapter.id} />
        </Branch>
      )}
    />
  );
}

function TopicBranch({ chapterId }: { chapterId: string }) {
  const topics = useTopics(chapterId);
  return (
    <BranchList
      query={topics}
      emptyLabel="No topics."
      render={(topic) => (
        <Branch key={topic.id} label={topic.name} heading="Topic">
          <SubTopicBranch topicId={topic.id} />
        </Branch>
      )}
    />
  );
}

function SubTopicBranch({ topicId }: { topicId: string }) {
  const subTopics = useSubTopics(topicId);
  return (
    <BranchList
      query={subTopics}
      emptyLabel="No sub-topics."
      render={(subTopic) => <li key={subTopic.id} className="text-sm text-foreground">SubTopic: {subTopic.name}</li>}
    />
  );
}
