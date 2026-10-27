"use client";

/**
 * Schedule authoring ({@code specs/13-P4-Assessment-V1.5.md §109}).
 *
 * <p>Lists, creates, edits the window of, and closes assessment schedules. A `datetime-local` value has
 * no zone, so it is interpreted in the operator's local zone and converted to an ISO-8601 instant. The
 * backend validates the final values and its message is surfaced verbatim.
 */
import { useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  useAssessment,
  useAssessments,
  useCloseSchedule,
  useCreateSchedule,
  useSchedules,
  useUpdateSchedule,
} from "@/features/assessment/hooks/use-assessment";
import { buildScheduleWindow, isoToLocalDateTime } from "@/features/assessment/lib/schedule-time";
import type { Schedule } from "@/features/assessment/schemas/assessment-builder";
import { ApiClientError } from "@/lib/errors";

const fieldClass =
  "w-full rounded-md border border-input px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

export function ScheduleList() {
  const schedules = useSchedules();
  const assessments = useAssessments();
  const createSchedule = useCreateSchedule();
  const updateSchedule = useUpdateSchedule();
  const closeSchedule = useCloseSchedule();

  const [assessmentId, setAssessmentId] = useState("");
  const [versionId, setVersionId] = useState("");
  const [mode, setMode] = useState<"PRACTICE" | "EXAM">("EXAM");
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const selectedAssessment = useAssessment(assessmentId);
  const versions = useMemo(() => selectedAssessment.data?.versions ?? [], [selectedAssessment.data]);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setMessage(null);

    if (versionId.length === 0) {
      setError("Choose an assessment version.");
      return;
    }

    const window = buildScheduleWindow({ startTime, endTime });
    if (!window.ok || !window.startTime || !window.endTime) {
      setError(window.error ?? "Enter a valid schedule window.");
      return;
    }

    try {
      await createSchedule.mutateAsync({
        assessmentVersionId: versionId,
        mode,
        startTime: window.startTime,
        endTime: window.endTime,
      });
      setMessage("Schedule created.");
      setStartTime("");
      setEndTime("");
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The schedule was rejected.");
    }
  }

  async function saveWindow(schedule: Schedule, nextStart: string, nextEnd: string) {
    setError(null);
    setMessage(null);
    const window = buildScheduleWindow({ startTime: nextStart, endTime: nextEnd });
    if (!window.ok || !window.startTime || !window.endTime) {
      setError(window.error ?? "Enter a valid schedule window.");
      return;
    }
    try {
      await updateSchedule.mutateAsync({ id: schedule.id, startTime: window.startTime, endTime: window.endTime });
      setMessage("Schedule window updated.");
    } catch (failure) {
      setError(failure instanceof ApiClientError ? failure.message : "The schedule window was rejected.");
    }
  }

  return (
    <div className="space-y-8">
      <form onSubmit={submit} className="space-y-3" aria-labelledby="schedule-create-heading">
        <h2 id="schedule-create-heading" className="text-lg font-semibold tracking-tight">
          Create a schedule
        </h2>

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <div>
            <label htmlFor="schedule-assessment" className="text-sm font-medium">
              Assessment
            </label>
            <select
              id="schedule-assessment"
              value={assessmentId}
              onChange={(event) => {
                setAssessmentId(event.target.value);
                setVersionId("");
              }}
              className={fieldClass}
            >
              <option value="">Choose an assessment</option>
              {(assessments.data ?? []).map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="schedule-version" className="text-sm font-medium">
              Assessment version
            </label>
            <select
              id="schedule-version"
              value={versionId}
              onChange={(event) => setVersionId(event.target.value)}
              disabled={assessmentId.length === 0}
              className={fieldClass}
            >
              <option value="">Choose a version</option>
              {versions.map((version) => (
                <option key={version.id} value={version.id}>
                  Version {version.version}
                  {version.published ? " (published)" : " (draft)"}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="schedule-mode" className="text-sm font-medium">
              Mode
            </label>
            <select
              id="schedule-mode"
              value={mode}
              onChange={(event) => setMode(event.target.value as "PRACTICE" | "EXAM")}
              className={fieldClass}
            >
              <option value="PRACTICE">PRACTICE</option>
              <option value="EXAM">EXAM</option>
            </select>
          </div>

          <div>
            <label htmlFor="schedule-start" className="text-sm font-medium">
              Start time
            </label>
            <input
              id="schedule-start"
              type="datetime-local"
              value={startTime}
              onChange={(event) => setStartTime(event.target.value)}
              className={fieldClass}
            />
          </div>

          <div>
            <label htmlFor="schedule-end" className="text-sm font-medium">
              End time
            </label>
            <input
              id="schedule-end"
              type="datetime-local"
              value={endTime}
              onChange={(event) => setEndTime(event.target.value)}
              className={fieldClass}
            />
          </div>
        </div>

        <Button type="submit" disabled={createSchedule.isPending}>
          Create schedule
        </Button>
      </form>

      {schedules.isPending ? <p role="status">Loading schedules…</p> : null}
      {schedules.isError ? (
        <p role="alert" className="text-sm text-destructive">
          Schedules could not be loaded.
        </p>
      ) : null}
      {schedules.data && schedules.data.length === 0 ? (
        <p role="note" className="text-sm text-muted-foreground">
          No schedules yet.
        </p>
      ) : null}

      {schedules.data && schedules.data.length > 0 ? (
        <div className="overflow-x-auto" role="region" aria-label="Scrollable table" tabIndex={0}>
<table className="w-full min-w-[640px] text-left text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="py-2">Version</th>
              <th className="py-2">Mode</th>
              <th className="py-2">Window</th>
              <th className="py-2">Status</th>
              <th className="py-2">Actions</th>
            </tr>
          </thead>
          <tbody>
            {schedules.data.map((schedule) => (
              <ScheduleRow
                key={schedule.id}
                schedule={schedule}
                onSave={saveWindow}
                onClose={(id) => void closeSchedule.mutateAsync(id)}
              />
            ))}
          </tbody>
        </table>
</div>
      ) : null}

      {message ? (
        <p role="status" className="text-sm text-success">
          {message}
        </p>
      ) : null}
      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}

function ScheduleRow({
  schedule,
  onSave,
  onClose,
}: {
  schedule: Schedule;
  onSave: (schedule: Schedule, start: string, end: string) => void;
  onClose: (id: string) => void;
}) {
  const [start, setStart] = useState(isoToLocalDateTime(schedule.startTime));
  const [end, setEnd] = useState(isoToLocalDateTime(schedule.endTime));

  return (
    <tr className="border-b border-border">
      <td className="py-2 font-mono text-xs">{schedule.assessmentVersionId}</td>
      <td className="py-2">{schedule.mode}</td>
      <td className="py-2">
        <div className="flex flex-wrap gap-2">
          <input
            aria-label={`Start time for schedule ${schedule.id}`}
            type="datetime-local"
            value={start}
            onChange={(event) => setStart(event.target.value)}
            className={fieldClass}
          />
          <input
            aria-label={`End time for schedule ${schedule.id}`}
            type="datetime-local"
            value={end}
            onChange={(event) => setEnd(event.target.value)}
            className={fieldClass}
          />
        </div>
      </td>
      <td className="py-2">{schedule.status}</td>
      <td className="py-2">
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => onSave(schedule, start, end)}>
            Save window
          </Button>
          <Button variant="ghost" size="sm" onClick={() => onClose(schedule.id)}>
            Close
          </Button>
        </div>
      </td>
    </tr>
  );
}
