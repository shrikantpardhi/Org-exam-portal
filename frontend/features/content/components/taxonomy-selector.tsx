"use client";

/**
 * Data-driven taxonomy cascade ({@code specs/12-P3-Content-V1.5.md §22, §102, §108}).
 *
 * <p>Exam → subject → chapter → topic → sub-topic are all chosen from the Academic masters. There
 * is no exam-name branching anywhere: changing the exam simply fetches that exam's subjects and
 * clears every stale descendant selection, so a chapter can never remain selected under a new
 * exam or subject.
 */
import {
  useChapters,
  useExams,
  useSubTopics,
  useSubjects,
  useTopics,
} from "@/features/academic/hooks/use-academic";

export interface TaxonomyValue {
  examId: string;
  subjectId: string;
  chapterId: string;
  topicId: string;
  subTopicId: string;
}

export const EMPTY_TAXONOMY: TaxonomyValue = {
  examId: "",
  subjectId: "",
  chapterId: "",
  topicId: "",
  subTopicId: "",
};

export function TaxonomySelector({
  value,
  onChange,
  errors,
  disabled = false,
}: {
  value: TaxonomyValue;
  onChange: (next: TaxonomyValue) => void;
  errors?: Partial<Record<keyof TaxonomyValue, string>>;
  disabled?: boolean;
}) {
  const exams = useExams();
  const subjects = useSubjects(value.examId);
  const chapters = useChapters(value.subjectId);
  const topics = useTopics(value.chapterId);
  const subTopics = useSubTopics(value.topicId);

  function changeExam(examId: string) {
    onChange({ ...EMPTY_TAXONOMY, examId });
  }

  function changeSubject(subjectId: string) {
    onChange({ ...value, subjectId, chapterId: "", topicId: "", subTopicId: "" });
  }

  function changeChapter(chapterId: string) {
    onChange({ ...value, chapterId, topicId: "", subTopicId: "" });
  }

  function changeTopic(topicId: string) {
    onChange({ ...value, topicId, subTopicId: "" });
  }

  return (
    <fieldset aria-label="Taxonomy" className="grid gap-3 sm:grid-cols-2">
      <Select
        label="Exam"
        value={value.examId}
        onChange={changeExam}
        disabled={disabled}
        error={errors?.examId}
        options={(exams.data ?? []).map((exam) => ({ id: exam.id, label: exam.name }))}
        placeholder="Select an exam"
        pending={exams.isPending}
      />
      <Select
        label="Subject"
        value={value.subjectId}
        onChange={changeSubject}
        disabled={disabled || value.examId.length === 0}
        error={errors?.subjectId}
        options={(subjects.data ?? []).map((subject) => ({ id: subject.id, label: subject.name }))}
        placeholder={value.examId ? "Select a subject" : "Select an exam first"}
        pending={subjects.isFetching}
      />
      <Select
        label="Chapter"
        value={value.chapterId}
        onChange={changeChapter}
        disabled={disabled || value.subjectId.length === 0}
        error={errors?.chapterId}
        options={(chapters.data ?? []).map((chapter) => ({ id: chapter.id, label: chapter.name }))}
        placeholder="Optional chapter"
        pending={chapters.isFetching}
      />
      <Select
        label="Topic"
        value={value.topicId}
        onChange={changeTopic}
        disabled={disabled || value.chapterId.length === 0}
        error={errors?.topicId}
        options={(topics.data ?? []).map((topic) => ({ id: topic.id, label: topic.name }))}
        placeholder="Optional topic"
        pending={topics.isFetching}
      />
      <Select
        label="Sub-topic"
        value={value.subTopicId}
        onChange={(subTopicId) => onChange({ ...value, subTopicId })}
        disabled={disabled || value.topicId.length === 0}
        error={errors?.subTopicId}
        options={(subTopics.data ?? []).map((subTopic) => ({ id: subTopic.id, label: subTopic.name }))}
        placeholder="Optional sub-topic"
        pending={subTopics.isFetching}
      />
    </fieldset>
  );
}

function Select({
  label,
  value,
  onChange,
  options,
  placeholder,
  disabled,
  error,
  pending,
}: {
  label: string;
  value: string;
  onChange: (next: string) => void;
  options: { id: string; label: string }[];
  placeholder: string;
  disabled?: boolean;
  error?: string;
  pending?: boolean;
}) {
  return (
    <div className="space-y-1">
      <label htmlFor={`taxonomy-${label}`} className="text-sm font-medium">
        {label}
      </label>
      <select
        id={`taxonomy-${label}`}
        aria-label={label}
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
        className="w-full rounded-md border border-input px-3 py-2 text-sm"
      >
        <option value="">{pending ? "Loading…" : placeholder}</option>
        {options.map((option) => (
          <option key={option.id} value={option.id}>
            {option.label}
          </option>
        ))}
      </select>
      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
