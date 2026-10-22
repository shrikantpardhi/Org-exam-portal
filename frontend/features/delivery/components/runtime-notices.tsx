"use client";

import { Button } from "@/components/ui/button";
import { unansweredCount, type PaletteCounts } from "@/features/delivery/lib/palette";

/**
 * Runtime banners and indicators ({@code specs/14-P5-Delivery-V1.5.md §130, §133, §134}).
 *
 * <p>Every state the specification requires has a visible, non-blocking representation. None of them
 * exposes an internal detail (a stack trace, an exception name, a table or endpoint name) to the
 * student (§130).
 */

/** The non-blocking offline indicator (§78, §130). */
export function OfflineIndicator({ pending, failed }: { pending: number; failed: number }) {
  return (
    <div role="status" aria-live="polite" className="rounded border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
      <p className="font-medium">You are offline.</p>
      <p>
        Your answers are kept on this device and will be sent when the connection returns. Do not close
        this tab.
      </p>
      {pending > 0 ? <p className="mt-1">{pending} answer(s) waiting to be sent.</p> : null}
      {failed > 0 ? (
        <p className="mt-1 font-medium">
          {failed} answer(s) could not be sent. Change the answer to try again.
        </p>
      ) : null}
    </div>
  );
}

/** The save indicator: distinguishes "saving" from "saved" from "not saved" (§130). */
export function SaveIndicator({
  saving,
  savedAt,
  failed,
  offline,
  pending = 0,
}: {
  saving: boolean;
  savedAt: string | null;
  failed: number;
  offline: boolean;
  /** Answers queued but not yet acknowledged. Non-zero means they are *not* saved. */
  pending?: number;
}) {
  // Order matters: an unacknowledged answer may never be reported as saved (§78). Only when the queue
  // is empty and nothing failed can the indicator claim everything is committed.
  const text = offline
    ? "Not saved yet — offline"
    : failed > 0
      ? "Not saved — will retry"
      : saving || pending > 0
        ? "Saving…"
        : savedAt
          ? `Saved ${savedAt}`
          : "All answers saved";
  return (
    <span
      role="status"
      aria-live="polite"
      className={
        failed > 0 && !offline ? "text-sm text-red-700" : "text-sm text-slate-600"
      }
    >
      {text}
    </span>
  );
}

/**
 * The session recovery banner (§134). Shown when a reload or reconnect found an existing session, so
 * the student knows their work was restored rather than restarted.
 */
export function SessionRecoveryBanner({ onDismiss }: { onDismiss: () => void }) {
  return (
    <div role="status" aria-live="polite" className="rounded border border-blue-300 bg-blue-50 p-3 text-sm text-blue-900">
      <p className="font-medium">Your session was recovered.</p>
      <p>Your saved answers and the timer have been restored from the server.</p>
      <Button variant="ghost" size="sm" className="mt-1" onClick={onDismiss}>
        Dismiss
      </Button>
    </div>
  );
}

/** Read-only notice for a second tab that did not win the single-owner lock (§77, §123). */
export function SecondTabNotice() {
  return (
    <div role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-900">
      <p className="font-medium">This examination is open in another tab.</p>
      <p>
        Only one tab may answer. This tab is read-only. Close the other tab and reload this one to
        continue here.
      </p>
    </div>
  );
}

/** The expired state, shown when the server deadline has passed (§130, §132). */
export function ExpiredNotice({ submitted }: { submitted: boolean }) {
  return (
    <div role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-900">
      <p className="font-medium">Time is up.</p>
      <p>
        {submitted
          ? "Your examination has been submitted."
          : "Your answers are being submitted now. Do not close this tab."}
      </p>
    </div>
  );
}

/**
 * The pre-submission confirmation (§133).
 *
 * <p>Shows the answered and unanswered counts and the review-marked count where the policy permits,
 * and allows cancellation when the policy permits. Deliberately does not show a score or any
 * correctness information (§74, §127).
 */
export function SubmissionDialog({
  counts,
  allowCancel,
  submitting,
  onConfirm,
  onCancel,
}: {
  counts: PaletteCounts;
  allowCancel: boolean;
  submitting: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const unanswered = unansweredCount(counts);
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="submit-heading"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
    >
      <div className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-5 shadow-lg">
        <h2 id="submit-heading" className="text-lg font-semibold tracking-tight">
          Submit this examination?
        </h2>
        <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
          <dt className="text-slate-600">Questions</dt>
          <dd className="font-medium">{counts.total}</dd>
          <dt className="text-slate-600">Answered</dt>
          <dd className="font-medium">{counts.answered}</dd>
          <dt className="text-slate-600">Not answered</dt>
          <dd className={unanswered > 0 ? "font-medium text-red-700" : "font-medium"}>{unanswered}</dd>
          <dt className="text-slate-600">Marked for review</dt>
          <dd className="font-medium">{counts.markedForReview}</dd>
        </dl>
        {unanswered > 0 ? (
          <p role="note" className="mt-3 text-sm text-amber-800">
            You have {unanswered} unanswered question{unanswered === 1 ? "" : "s"}. You cannot change
            your answers after submitting.
          </p>
        ) : (
          <p role="note" className="mt-3 text-sm text-slate-600">
            You cannot change your answers after submitting.
          </p>
        )}
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="outline" disabled={!allowCancel || submitting} onClick={onCancel}>
            Keep working
          </Button>
          <Button disabled={submitting} onClick={onConfirm}>
            {submitting ? "Submitting…" : "Submit"}
          </Button>
        </div>
      </div>
    </div>
  );
}
