"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { RuntimeTimer } from "@/features/delivery/components/runtime-timer";
import { getResponse } from "@/features/delivery/api/delivery-api";
import { useAttempt, useAttemptQuestions, useSaveResponse, useSubmitAttempt } from "@/features/delivery/hooks/use-delivery";
import { isStaleResponse } from "@/features/delivery/lib/runtime-helpers";
import type { AttemptQuestion } from "@/features/delivery/schemas/delivery";
import { ApiClientError } from "@/lib/errors";

type SaveStatus = "idle" | "saving" | "saved" | "stale" | "error";

interface Answer {
  response: string | null;
  markedForReview: boolean;
  visited: boolean;
}

const EMPTY_ANSWER: Answer = { response: null, markedForReview: false, visited: true };

/**
 * The engine-agnostic runtime ({@code specs/14-P5-Delivery-V1.5.md §73-§80, §130-§135}).
 *
 * <p>It renders only what the normalized configuration permits, drives an ETag-protected autosave,
 * recovers safely on reload (all state is server-backed), and treats submission as terminal.
 */
export function ExamRunner({ attemptId }: { attemptId: string }) {
  const attempt = useAttempt(attemptId);
  const questions = useAttemptQuestions(attemptId);
  const save = useSaveResponse(attemptId);
  const submit = useSubmitAttempt(attemptId);

  const [index, setIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<string, Answer>>({});
  const [etags, setEtags] = useState<Record<string, string>>({});
  const [statuses, setStatuses] = useState<Record<string, SaveStatus>>({});
  const timers = useRef<Record<string, ReturnType<typeof setTimeout>>>({});
  const answersRef = useRef(answers);
  answersRef.current = answers;

  useEffect(() => {
    const pending = timers.current;
    return () => {
      Object.values(pending).forEach((timer) => clearTimeout(timer));
    };
  }, []);

  const list = questions.data ?? [];
  const current = list[index];
  const configuration = attempt.data?.deliveryConfiguration;
  const deadline = attempt.data?.timer.deadline ?? null;
  const serverTime = attempt.data?.serverTime ?? new Date().toISOString();
  const practice = configuration?.capabilities.practice ?? false;

  const persist = useCallback(
    async (question: AttemptQuestion, answer: Answer) => {
      const key = question.questionVersionId;
      setStatuses((prev) => ({ ...prev, [key]: "saving" }));
      try {
        const result = await save.mutateAsync({
          questionVersionId: key,
          body: {
            response: answer.response,
            timeSpentSeconds: null,
            markedForReview: answer.markedForReview,
            visited: true,
          },
          ifMatch: etags[key],
        });
        setEtags((prev) => ({ ...prev, [key]: result.etag }));
        setStatuses((prev) => ({ ...prev, [key]: "saved" }));
      } catch (failure) {
        const stale =
          failure instanceof ApiClientError &&
          (failure.status === 412 || isStaleResponse({ errors: failure.fieldErrors }));
        if (stale) {
          setStatuses((prev) => ({ ...prev, [key]: "stale" }));
          // Re-fetch the current response and retry once with the fresh ETag; an older request never
          // overwrites a newer accepted response (specs/14-P5-Delivery-V1.5.md §43).
          try {
            const fresh = await getResponse(attemptId, key);
            setEtags((prev) => ({ ...prev, [key]: fresh.etag }));
            const retried = await save.mutateAsync({
              questionVersionId: key,
              body: {
                response: answersRef.current[key]?.response ?? answer.response,
                timeSpentSeconds: null,
                markedForReview: answersRef.current[key]?.markedForReview ?? answer.markedForReview,
                visited: true,
              },
              ifMatch: fresh.etag,
            });
            setEtags((prev) => ({ ...prev, [key]: retried.etag }));
            setStatuses((prev) => ({ ...prev, [key]: "saved" }));
          } catch {
            setStatuses((prev) => ({ ...prev, [key]: "error" }));
          }
          return;
        }
        setStatuses((prev) => ({ ...prev, [key]: "error" }));
      }
    },
    [attemptId, etags, save],
  );

  const queueSave = useCallback(
    (question: AttemptQuestion, patch: Partial<Answer>) => {
      const key = question.questionVersionId;
      const next = { ...EMPTY_ANSWER, ...answersRef.current[key], ...patch };
      setAnswers((prev) => ({ ...prev, [key]: next }));
      answersRef.current = { ...answersRef.current, [key]: next };
      clearTimeout(timers.current[key]);
      const delayMs = Math.max(0, (configuration?.validation.autoSaveIntervalSeconds ?? 10) * 1000);
      timers.current[key] = setTimeout(() => void persist(question, next), delayMs);
    },
    [configuration?.validation.autoSaveIntervalSeconds, persist],
  );

  if (attempt.isLoading || questions.isLoading) {
    return <p role="status">Loading examination…</p>;
  }
  if (attempt.isError || questions.isError) {
    return <p role="alert">The examination could not be loaded. Reload to recover your attempt.</p>;
  }
  if (!attempt.data || !configuration) {
    return <p role="alert">The examination is unavailable.</p>;
  }
  if (attempt.data.attemptStatus === "SUBMITTED" || attempt.data.attemptStatus === "EVALUATED") {
    return <p role="status">This attempt has been submitted.</p>;
  }
  if (!current) {
    return <p role="status">No questions are available.</p>;
  }

  const answer = { ...EMPTY_ANSWER, ...answers[current.questionVersionId] };
  const status = statuses[current.questionVersionId] ?? "idle";

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <p className="text-sm text-slate-600">
          Question {index + 1} of {list.length}
          {current.sectionName ? ` · ${current.sectionName}` : ""}
        </p>
        {configuration.presentation.showTimer ? (
          <RuntimeTimer deadline={deadline} serverTime={serverTime} />
        ) : null}
      </div>

      <article className="rounded border border-slate-200 p-4">
        <p data-testid="question-content">{current.renderedContent ?? current.content}</p>
        <ul className="mt-3 space-y-2">
          {current.options.map((option) => (
            <li key={option.optionId}>
              <label className="flex items-center gap-2">
                <input
                  type="radio"
                  name={current.questionVersionId}
                  checked={answer.response === option.optionKey}
                  onChange={() =>
                    queueSave(current, { response: option.optionKey, visited: true })
                  }
                />
                <span>{option.optionKey}</span>
                <span>{option.renderedContent ?? option.content}</span>
              </label>
            </li>
          ))}
        </ul>
      </article>

      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant="outline"
          disabled={!configuration.navigation.allowBackNavigation || index === 0}
          onClick={() => setIndex((value) => Math.max(0, value - 1))}
        >
          Previous
        </Button>
        <Button
          variant="outline"
          disabled={index >= list.length - 1}
          onClick={() => setIndex((value) => Math.min(list.length - 1, value + 1))}
        >
          Next
        </Button>
        {configuration.capabilities.allowMarkForReview ? (
          <Button
            variant="outline"
            onClick={() => queueSave(current, { markedForReview: !answer.markedForReview })}
          >
            {answer.markedForReview ? "Unmark review" : "Mark for review"}
          </Button>
        ) : null}
        {configuration.capabilities.allowClearResponse ? (
          <Button variant="outline" onClick={() => queueSave(current, { response: null })}>
            Clear
          </Button>
        ) : null}
        <span role="status" aria-live="polite" className="text-sm text-slate-600">
          {status === "saving"
            ? "Saving…"
            : status === "saved"
              ? "Saved"
              : status === "stale"
                ? "Resyncing…"
                : status === "error"
                  ? "Not saved — will retry on next change"
                  : ""}
        </span>
      </div>

      <div className="flex items-center gap-2">
        <Button
          disabled={submit.isPending}
          onClick={() => void submit.mutateAsync().catch(() => undefined)}
        >
          {practice ? "Finish practice" : "Submit examination"}
        </Button>
        {submit.isSuccess ? <p role="status">Assessment submitted successfully.</p> : null}
        {submit.isError ? <p role="alert">Submission failed. Please retry.</p> : null}
      </div>
    </div>
  );
}
