"use client";

/**
 * Previous exam history editor ({@code specs/12-P3-Content-V1.5.md §36, §108}).
 *
 * <p>The exam is always chosen from the Academic Exam masters and submitted as {@code examId};
 * there is no free-text exam name field, so the same question can be tagged to any exam without
 * hard-coding exam names. Year, shift and source are optional metadata. Rows are added, edited and
 * removed through the frozen previous-exam endpoints.
 */
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { useExams } from "@/features/academic/hooks/use-academic";
import {
  useAddPreviousExam,
  useDeletePreviousExam,
  usePreviousExams,
  useUpdatePreviousExam,
} from "@/features/content/hooks/use-content";
import { buildPreviousExamRequest, buildPreviousExamUpdateRequest } from "@/features/content/lib/payload";
import type { PreviousExam } from "@/features/content/schemas/content";
import { ApiClientError } from "@/lib/errors";

interface RowDraft {
  examId: string;
  year: string;
  shift: string;
  source: string;
}

const EMPTY_ROW: RowDraft = { examId: "", year: "", shift: "", source: "" };

export function PreviousExamEditor({
  questionId,
  disabled = false,
}: {
  questionId: string;
  disabled?: boolean;
}) {
  const exams = useExams();
  const history = usePreviousExams(questionId);
  const add = useAddPreviousExam(questionId);
  const update = useUpdatePreviousExam(questionId);
  const remove = useDeletePreviousExam(questionId);

  const [draft, setDraft] = useState<RowDraft>(EMPTY_ROW);
  const [editing, setEditing] = useState<{ historyId: string; row: RowDraft } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);

  function examName(examId: string): string {
    return (exams.data ?? []).find((exam) => exam.id === examId)?.name ?? examId;
  }

  async function handleAdd() {
    setError(null);
    setStatus(null);
    if (draft.examId.length === 0) {
      setError("Select an exam from the masters.");
      return;
    }
    try {
      await add.mutateAsync(buildPreviousExamRequest(draft));
      setDraft(EMPTY_ROW);
      setStatus("Previous exam added.");
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The row could not be added.");
    }
  }

  async function handleSave(historyId: string) {
    setError(null);
    setStatus(null);
    if (!editing) {
      return;
    }
    try {
      await update.mutateAsync({
        historyId,
        body: buildPreviousExamUpdateRequest(editing.row),
      });
      setEditing(null);
      setStatus("Previous exam updated.");
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The row could not be updated.");
    }
  }

  async function handleRemove(row: PreviousExam) {
    setError(null);
    setStatus(null);
    try {
      await remove.mutateAsync(row.id);
      setStatus("Previous exam removed.");
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The row could not be removed.");
    }
  }

  return (
    <section aria-label="Previous exam history" className="space-y-3">
      <h2 className="text-lg font-semibold">Previous exam history</h2>
      <p className="text-xs text-slate-500">
        The exam is selected from the masters and stored by id — an exam name is never typed here.
      </p>

      {history.isPending ? (
        <p role="status" className="text-sm text-slate-500">
          Loading previous exams…
        </p>
      ) : null}

      {history.data && history.data.length === 0 ? (
        <p className="text-sm text-slate-500">No previous exam recorded.</p>
      ) : null}

      {history.data && history.data.length > 0 ? (
        <table aria-label="Previous exams" className="w-full text-sm">
          <thead>
            <tr className="text-left">
              <th scope="col">Exam</th>
              <th scope="col">Year</th>
              <th scope="col">Shift</th>
              <th scope="col">Source</th>
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            {history.data.map((row) => (
              <tr key={row.id}>
                <td>{examName(row.examId)}</td>
                <td>{editing?.historyId === row.id ? (
                  <input
                    aria-label={`Year for ${examName(row.examId)}`}
                    value={editing.row.year}
                    onChange={(event) =>
                      setEditing({ ...editing, row: { ...editing.row, year: event.target.value } })
                    }
                    className="w-24 rounded-md border border-slate-300 px-2 py-1"
                  />
                ) : (row.year ?? "—")}</td>
                <td>{editing?.historyId === row.id ? (
                  <input
                    aria-label={`Shift for ${examName(row.examId)}`}
                    value={editing.row.shift}
                    onChange={(event) =>
                      setEditing({ ...editing, row: { ...editing.row, shift: event.target.value } })
                    }
                    className="w-24 rounded-md border border-slate-300 px-2 py-1"
                  />
                ) : (row.shift ?? "—")}</td>
                <td>{editing?.historyId === row.id ? (
                  <input
                    aria-label={`Source for ${examName(row.examId)}`}
                    value={editing.row.source}
                    onChange={(event) =>
                      setEditing({ ...editing, row: { ...editing.row, source: event.target.value } })
                    }
                    className="w-32 rounded-md border border-slate-300 px-2 py-1"
                  />
                ) : (row.source ?? "—")}</td>
                <td className="space-x-2">
                  {editing?.historyId === row.id ? (
                    <>
                      <Button variant="outline" size="sm" disabled={disabled} onClick={() => void handleSave(row.id)}>
                        Save
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setEditing(null)}>
                        Cancel
                      </Button>
                    </>
                  ) : (
                    <>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={disabled}
                        aria-label={`Edit previous exam ${examName(row.examId)}`}
                        onClick={() =>
                          setEditing({
                            historyId: row.id,
                            row: {
                              examId: row.examId,
                              year: row.year === null || row.year === undefined ? "" : String(row.year),
                              shift: row.shift ?? "",
                              source: row.source ?? "",
                            },
                          })
                        }
                      >
                        Edit
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={disabled}
                        aria-label={`Remove previous exam ${examName(row.examId)}`}
                        onClick={() => void handleRemove(row)}
                        className="text-red-600"
                      >
                        Remove
                      </Button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}

      <fieldset aria-label="Add previous exam" className="grid gap-2 sm:grid-cols-4">
        <label className="space-y-1 text-sm">
          <span className="font-medium">Exam</span>
          <select
            aria-label="Previous exam name"
            value={draft.examId}
            disabled={disabled}
            onChange={(event) => setDraft({ ...draft, examId: event.target.value })}
            className="w-full rounded-md border border-slate-300 px-2 py-1 text-sm"
          >
            <option value="">Select an exam</option>
            {(exams.data ?? []).map((exam) => (
              <option key={exam.id} value={exam.id}>
                {exam.name}
              </option>
            ))}
          </select>
        </label>
        <label className="space-y-1 text-sm">
          <span className="font-medium">Year</span>
          <input
            aria-label="Previous exam year"
            value={draft.year}
            inputMode="numeric"
            disabled={disabled}
            onChange={(event) => setDraft({ ...draft, year: event.target.value })}
            className="w-full rounded-md border border-slate-300 px-2 py-1 text-sm"
          />
        </label>
        <label className="space-y-1 text-sm">
          <span className="font-medium">Shift</span>
          <input
            aria-label="Previous exam shift"
            value={draft.shift}
            disabled={disabled}
            onChange={(event) => setDraft({ ...draft, shift: event.target.value })}
            className="w-full rounded-md border border-slate-300 px-2 py-1 text-sm"
          />
        </label>
        <label className="space-y-1 text-sm">
          <span className="font-medium">Source</span>
          <input
            aria-label="Previous exam source"
            value={draft.source}
            disabled={disabled}
            onChange={(event) => setDraft({ ...draft, source: event.target.value })}
            className="w-full rounded-md border border-slate-300 px-2 py-1 text-sm"
          />
        </label>
      </fieldset>

      <Button disabled={disabled || add.isPending} aria-label="Add previous exam" onClick={() => void handleAdd()}>
        {add.isPending ? "Adding…" : "Add previous exam"}
      </Button>

      {error ? (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      ) : null}
      {status ? (
        <p role="status" className="text-sm text-slate-600">
          {status}
        </p>
      ) : null}
    </section>
  );
}
