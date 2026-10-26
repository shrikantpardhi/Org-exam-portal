"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { QuestionPalette } from "@/features/delivery/components/question-palette";
import { RuntimeTimer } from "@/features/delivery/components/runtime-timer";
import { SectionNavigator } from "@/features/delivery/components/section-navigator";
import {
  ExpiredNotice,
  OfflineIndicator,
  SaveIndicator,
  SecondTabNotice,
  SessionRecoveryBanner,
  SubmissionDialog,
} from "@/features/delivery/components/runtime-notices";
import { getResponse } from "@/features/delivery/api/delivery-api";
import {
  useAttempt,
  useAttemptQuestions,
  useSaveResponse,
  useSubmitAttempt,
} from "@/features/delivery/hooks/use-delivery";
import { useMultiTabGuard } from "@/features/delivery/hooks/use-multi-tab-guard";
import { useNetworkStatus } from "@/features/delivery/hooks/use-network-status";
import { groupBySection, paletteCounts, questionStatus } from "@/features/delivery/lib/palette";
import { ResponseQueue, flushQueue } from "@/features/delivery/lib/response-queue";
import { isStaleResponse } from "@/features/delivery/lib/runtime-helpers";
import {
  buildTimerAnchor,
  reconcileAnchor,
  remainingFromAnchor,
  shouldAutoSubmit,
  timerSeverity,
  type TimerAnchor,
} from "@/features/delivery/lib/server-clock";
import type { AttemptQuestion, ResponseSaveRequest } from "@/features/delivery/schemas/delivery";
import { ApiClientError } from "@/lib/errors";

/** How the runtime describes one question's local interaction state. */
interface UiAnswer {
  response: string | null;
  markedForReview: boolean;
  visited: boolean;
}

const EMPTY_ANSWER: UiAnswer = { response: null, markedForReview: false, visited: false };

/**
 * The full engine-agnostic examination runtime
 * ({@code specs/14-P5-Delivery-V1.5.md §73-§79, §130-§135}).
 *
 * <p>Three state layers are kept deliberately separate, as §135 requires:
 * <ul>
 *   <li><b>server state</b> — the attempt, its questions and the responses, all from TanStack Query;</li>
 *   <li><b>UI state</b> — the selected question, the dialog, and the student's not-yet-acknowledged
 *       edits;</li>
 *   <li><b>network state</b> — online/offline, pending and failed saves.</li>
 * </ul>
 *
 * <p>The runtime is a renderer and interaction client (§74). It never scores, never reveals
 * correctness, never reconstructs a snapshot, and never decides the deadline: the timer is corrected
 * against the server clock and may only move earlier.
 */
export function ExamRuntime({ attemptId }: { attemptId: string }) {
  const attempt = useAttempt(attemptId);
  const questionsQuery = useAttemptQuestions(attemptId);
  const save = useSaveResponse(attemptId);
  const submit = useSubmitAttempt(attemptId);

  const { online } = useNetworkStatus();
  const { ownership } = useMultiTabGuard(attemptId);
  const readOnly = ownership === "blocked";

  // ----- UI state -----
  const [index, setIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<string, UiAnswer>>({});
  const [etags, setEtags] = useState<Record<string, string>>({});
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [recovered, setRecovered] = useState(false);
  const [dismissedRecovery, setDismissedRecovery] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  // ----- network state -----
  const [queue, setQueue] = useState(() => ResponseQueue.empty<ResponseSaveRequest>());
  const [saving, setSaving] = useState(false);
  const [savedAt, setSavedAt] = useState<string | null>(null);

  // ----- refs holding the latest values for async callbacks -----
  const answersRef = useRef(answers);
  answersRef.current = answers;
  const etagsRef = useRef(etags);
  etagsRef.current = etags;
  const queueRef = useRef(queue);
  queueRef.current = queue;
  const autoSubmitted = useRef(false);
  const revalidating = useRef(false);

  // Memoized so every hook and callback that depends on the question list sees a stable reference.
  const list = useMemo(() => questionsQuery.data ?? [], [questionsQuery.data]);
  const configuration = attempt.data?.deliveryConfiguration;

  // The deadline may only move earlier than what is already displayed (§132). The anchor pins the
  // server-computed remaining time to the instant the payload arrived, so the countdown advances
  // locally without the browser ever becoming the deadline authority.
  const [anchor, setAnchor] = useState<TimerAnchor | null>(null);
  const serverDeadline = attempt.data?.timer.deadline ?? null;
  const serverTime = attempt.data?.serverTime ?? null;
  useEffect(() => {
    if (!serverDeadline && !serverTime) {
      return;
    }
    setAnchor((previous) =>
      reconcileAnchor(previous, buildTimerAnchor(serverDeadline, serverTime, Date.now())),
    );
  }, [serverDeadline, serverTime]);

  const durationSeconds = attempt.data?.timer.durationSeconds ?? null;
  const remaining = remainingFromAnchor(anchor, now);
  const severity = timerSeverity(remaining, durationSeconds);

  // One tick per second drives the display; the deadline itself comes from the server.
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, []);

  // Recover server-backed response state on load/reload (§79, §134). Marked visited questions come
  // from the server, so a refresh restores the palette rather than resetting it.
  useEffect(() => {
    if (!attempt.data) {
      return;
    }
    if (attempt.data.attemptStatus === "IN_PROGRESS" && !recovered) {
      setRecovered(true);
      if (attempt.data.startedOn && Date.parse(attempt.data.startedOn) < Date.now() - 5_000) {
        setDismissedRecovery(false);
      }
    }
  }, [attempt.data, recovered]);

  // Terminally submitted attempts are read-only, including after a reload (§133). The local
  // `submit.isSuccess` is included so the runtime locks immediately on acknowledgement rather than
  // waiting for the invalidated attempt query to refetch — a window in which the student could still
  // edit an attempt the server has already closed.
  const submitted =
    attempt.data?.attemptStatus === "SUBMITTED" ||
    attempt.data?.attemptStatus === "EVALUATED" ||
    submit.isSuccess;

  /** Persists one answer, honouring the ETag and retrying once on a stale precondition (§43). */
  const persist = useCallback(
    async (question: AttemptQuestion, payload: ResponseSaveRequest): Promise<void> => {
      const key = question.questionVersionId;
      try {
        const result = await save.mutateAsync({
          questionVersionId: key,
          body: payload,
          ifMatch: etagsRef.current[key],
        });
        setEtags((previous) => ({ ...previous, [key]: result.etag }));
      } catch (failure) {
        const stale =
          failure instanceof ApiClientError &&
          (failure.status === 412 || isStaleResponse({ errors: failure.fieldErrors }));
        if (!stale) {
          throw failure;
        }
        // Re-read the current response, adopt its ETag, and retry with the newest local value.
        const fresh = await getResponse(attemptId, key);
        setEtags((previous) => ({ ...previous, [key]: fresh.etag }));
        const latest = answersRef.current[key] ?? { ...EMPTY_ANSWER };
        const result = await save.mutateAsync({
          questionVersionId: key,
          body: {
            response: latest.response,
            timeSpentSeconds: null,
            markedForReview: latest.markedForReview,
            visited: true,
          },
          ifMatch: fresh.etag,
        });
        setEtags((previous) => ({ ...previous, [key]: result.etag }));
      }
    },
    [attemptId, save],
  );

  /** Drains the offline queue at a bounded rate and marks success only on acknowledgement (§78). */
  const drain = useCallback(async () => {
    if (revalidating.current) {
      return;
    }
    revalidating.current = true;
    setSaving(true);
    try {
      const result = await flushQueue(
        queueRef.current,
        async (entry) => {
          const question = list.find((item) => item.questionVersionId === entry.questionVersionId);
          if (!question) {
            return;
          }
          await persist(question, entry.payload);
        },
        Date.now(),
      );
      setQueue(result.queue);
      if (result.saved.length > 0) {
        setSavedAt(new Date().toLocaleTimeString());
      }
    } finally {
      revalidating.current = false;
      setSaving(false);
    }
  }, [list, persist]);

  // Retry when the connection comes back, and on a slow interval while anything is pending.
  useEffect(() => {
    if (!online || queue.size === 0) {
      return;
    }
    void drain();
    const id = window.setInterval(() => void drain(), 3_000);
    return () => window.clearInterval(id);
  }, [online, queue.size, drain]);

  /** Applies a local edit and queues the save. Latest value per question wins (§77). */
  const edit = useCallback(
    (question: AttemptQuestion, patch: Partial<UiAnswer>) => {
      if (readOnly || submitted) {
        return;
      }
      const key = question.questionVersionId;
      const next: UiAnswer = { ...EMPTY_ANSWER, ...answersRef.current[key], ...patch, visited: true };
      const nextAnswers = { ...answersRef.current, [key]: next };
      setAnswers(nextAnswers);
      answersRef.current = nextAnswers;
      setQueue((previous) =>
        previous.enqueue(
          key,
          {
            response: next.response,
            timeSpentSeconds: null,
            markedForReview: next.markedForReview,
            visited: true,
          },
          Date.now(),
        ),
      );
    },
    [readOnly, submitted],
  );

  /** Sends a just-queued answer promptly; the auto-save interval is the fallback, not the trigger. */
  useEffect(() => {
    if (queue.size === 0 || !online) {
      return;
    }
    const id = window.setTimeout(() => void drain(), Math.max(0, (configuration?.validation.autoSaveIntervalSeconds ?? 10) * 1000));
    return () => window.clearTimeout(id);
  }, [queue.size, online, configuration?.validation.autoSaveIntervalSeconds, drain]);

  /** Submits, then makes the runtime read-only and clears the queue (§133). */
  const doSubmit = useCallback(async () => {
    if (submitted) {
      return;
    }
    setConfirmOpen(false);
    try {
      await submit.mutateAsync();
      setQueue((previous) => previous.clear());
    } catch {
      // The failure surfaces through `submit.isError`; the student may retry.
    }
  }, [submit, submitted]);

  // Auto-submit exactly once when the server deadline passes and the policy allows it (§55).
  useEffect(() => {
    if (autoSubmitted.current || submitted || !configuration) {
      return;
    }
    if (shouldAutoSubmit(remaining, configuration.submission.autoSubmitOnExpiry, submitted)) {
      autoSubmitted.current = true;
      void doSubmit();
    }
  }, [remaining, configuration, submitted, doSubmit]);

  // A single stream: re-validate a stale response and warn before leaving with unsaved work (§77, §78).
  useEffect(() => {
    const handler = (event: BeforeUnloadEvent) => {
      if (queue.size > 0) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [queue.size]);

  const statuses = useMemo(() => {
    return list.map((question) => {
      const answer = { ...EMPTY_ANSWER, ...answers[question.questionVersionId] };
      return questionStatus({
        visited: answer.visited || answer.response !== null,
        answered: answer.response !== null && answer.response !== "",
        markedForReview: answer.markedForReview,
      });
    });
  }, [list, answers]);

  const counts = useMemo(() => paletteCounts(statuses), [statuses]);

  const sections = useMemo(
    () =>
      groupBySection(
        list.map((question, position) => ({
          sectionName: question.sectionName,
          displayOrder: question.displayOrder,
          id: question.questionVersionId,
          position,
        })),
        configuration?.presentation.sectionOrder ?? [],
      ),
    [list, configuration?.presentation.sectionOrder],
  );

  // ----- render -----

  if (attempt.isLoading || questionsQuery.isLoading) {
    return (
      <p role="status" aria-live="polite" className="text-sm text-muted-foreground">
        Loading your examination…
      </p>
    );
  }
  if (attempt.isError || questionsQuery.isError) {
    return (
      <p role="alert" className="rounded border border-destructive bg-destructive-subtle p-3 text-sm text-destructive">
        Your examination could not be loaded. Reload the page to recover your attempt — your saved
        answers are on the server.
      </p>
    );
  }
  if (!attempt.data || !configuration) {
    return (
      <p role="alert" className="rounded border border-destructive bg-destructive-subtle p-3 text-sm text-destructive">
        This examination is not available. Contact support if this continues.
      </p>
    );
  }

  if (submitted) {
    return (
      <div className="flex flex-col gap-3">
        <div role="status" className="rounded border border-success bg-success-subtle p-3 text-sm text-emerald-900">
          <p className="font-medium">Your examination has been submitted.</p>
          <p>You can no longer change your answers.</p>
        </div>
        <p className="text-sm text-muted-foreground">
          {counts.answered} of {counts.total} questions were answered.
        </p>
      </div>
    );
  }

  const current = list[index];
  if (!current) {
    return <p role="status">No questions are available for this examination.</p>;
  }

  const answer = { ...EMPTY_ANSWER, ...answers[current.questionVersionId] };
  const failures = queue.failures().length;
  const practice = configuration.capabilities.practice;

  return (
    <div className="flex flex-col gap-4">
      {!online ? (
        <OfflineIndicator pending={queue.size - failures} failed={failures} />
      ) : null}
      {readOnly ? <SecondTabNotice /> : null}
      {recovered && !dismissedRecovery ? (
        <SessionRecoveryBanner onDismiss={() => setDismissedRecovery(true)} />
      ) : null}
      {severity === "expired" ? <ExpiredNotice submitted={submit.isSuccess} /> : null}

      <header className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground">
          Question {index + 1} of {list.length}
          {current.sectionName ? ` · ${current.sectionName}` : ""}
        </p>
        <div className="flex items-center gap-3">
          <SaveIndicator
            saving={saving}
            savedAt={savedAt}
            failed={failures}
            offline={!online}
            pending={queue.size - failures}
          />
          {configuration.presentation.showTimer ? (
            <RuntimeTimer
              remaining={remaining}
              serverTime={attempt.data.serverTime}
              severity={severity}
              durationSeconds={durationSeconds}
            />
          ) : null}
        </div>
      </header>

      {configuration.navigation.allowSectionSwitch && sections.length > 1 ? (
        <SectionNavigator
          sections={sections}
          currentQuestionId={current.questionVersionId}
          onJump={(questionVersionId) => {
            const target = list.findIndex((item) => item.questionVersionId === questionVersionId);
            if (target >= 0) {
              setIndex(target);
            }
          }}
        />
      ) : null}

      <div className="flex flex-col gap-4 md:flex-row">
        <article className="flex-1 rounded border border-border p-4">
          <p data-testid="question-content" className="text-base">
            {current.renderedContent ?? current.content}
          </p>
          <fieldset className="mt-3 space-y-2" disabled={readOnly}>
            <legend className="sr-only">Answer options</legend>
            {current.options.map((option) => (
              <label key={option.optionId} className="flex items-center gap-2">
                <input
                  type="radio"
                  name={current.questionVersionId}
                  checked={answer.response === option.optionKey}
                  onChange={() => edit(current, { response: option.optionKey })}
                />
                <span className="font-medium">{option.optionKey}</span>
                <span>{option.renderedContent ?? option.content}</span>
              </label>
            ))}
          </fieldset>
        </article>

        {configuration.presentation.showPalette ? (
          <aside className="md:w-64">
            <QuestionPalette
              statuses={statuses}
              currentIndex={index}
              disabled={readOnly}
              onJump={setIndex}
            />
          </aside>
        ) : null}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant="outline"
          disabled={readOnly || index === 0 || !configuration.navigation.allowBackNavigation}
          onClick={() => setIndex((value) => Math.max(0, value - 1))}
        >
          Previous
        </Button>
        <Button
          variant="outline"
          disabled={readOnly || index >= list.length - 1}
          onClick={() => setIndex((value) => Math.min(list.length - 1, value + 1))}
        >
          Next
        </Button>
        {configuration.capabilities.allowMarkForReview ? (
          <Button
            variant="outline"
            disabled={readOnly}
            onClick={() => edit(current, { markedForReview: !answer.markedForReview })}
          >
            {answer.markedForReview ? "Unmark for review" : "Mark for review"}
          </Button>
        ) : null}
        {configuration.capabilities.allowClearResponse ? (
          <Button
            variant="outline"
            disabled={readOnly}
            onClick={() => edit(current, { response: null })}
          >
            Clear response
          </Button>
        ) : null}
        {configuration.submission.allowManualSubmit ? (
          <Button
            className="ml-auto"
            disabled={readOnly || submit.isPending}
            onClick={() => setConfirmOpen(true)}
          >
            {practice ? "Finish practice" : "Submit examination"}
          </Button>
        ) : null}
      </div>

      {submit.isError ? (
        <p role="alert" className="text-sm text-destructive">
          Submission failed. Your answers are safe — please try again.
        </p>
      ) : null}

      {confirmOpen ? (
        <SubmissionDialog
          counts={counts}
          // Cancelling is always permitted: a confirmation dialog the student cannot dismiss would
          // trap them. The policy's `requireConfirmation` decides whether the dialog appears at all.
          allowCancel
          submitting={submit.isPending}
          onCancel={() => setConfirmOpen(false)}
          onConfirm={() => void doSubmit()}
        />
      ) : null}
    </div>
  );
}
