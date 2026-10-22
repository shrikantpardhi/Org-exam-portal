/**
 * The bounded offline queue for response saves ({@code specs/14-P5-Delivery-V1.5.md §77, §78}).
 *
 * <p>§77 requires <em>one mutation stream per active attempt</em>: a burst of clicks, route changes or
 * re-renders must not spawn concurrent writes for the same question. §78 requires that a temporary
 * disconnection retains UI state, queues safe pending mutations, retries with bounded backoff, and
 * marks the response persisted only when the server acknowledges it.
 *
 * <p>Two invariants drive this design:
 * <ul>
 *   <li><b>Latest value wins per question.</b> The queue is keyed by {@code questionVersionId}, so a
 *       newer answer replaces an older queued one instead of being appended behind it. A student who
 *       changes their mind five times while offline sends one save on reconnect, not five.</li>
 *   <li><b>Never fake a save.</b> An entry leaves the queue only on server acknowledgement. If the
 *       attempt budget is exhausted the entry is marked {@link QueueEntryState#Failed}, never
 *       {@code Saved}, so the UI cannot present a lost answer as committed (§78).</li>
 * </ul>
 *
 * <p>Pure and transport-free: the caller injects the save function and the clock, so every bound is
 * testable without a browser or a network.
 */

/** The lifecycle of one queued save. Distinct from the UI's own notion of "dirty". */
export type QueueEntryState = "Pending" | "Sending" | "Failed";

export interface QueuedSave<TPayload> {
  readonly questionVersionId: string;
  readonly payload: TPayload;
  readonly state: QueueEntryState;
  readonly attempts: number;
  readonly nextAttemptAtMs: number;
}

/** Bounded retry policy. Deliberately finite: §78 forbids queueing indefinitely. */
export const MAX_SAVE_ATTEMPTS = 5;
export const BASE_BACKOFF_MS = 1_000;
export const MAX_BACKOFF_MS = 30_000;

/**
 * Exponential backoff with a ceiling, derived purely from the attempt number so it is deterministic
 * and testable: 1s, 2s, 4s, 8s, 16s (capped at 30s).
 */
export function backoffDelayMs(attempt: number): number {
  const safeAttempt = Math.max(1, Math.floor(attempt));
  const raw = BASE_BACKOFF_MS * Math.pow(2, safeAttempt - 1);
  return Math.min(raw, MAX_BACKOFF_MS);
}

/** A serializable snapshot of the queue, safe to persist across a reload. */
export type QueueSnapshot<TPayload> = Record<string, { payload: TPayload; attempts: number }>;

/**
 * The queue itself. Immutable operations returning new instances keep React state updates and tests
 * simple, and make it impossible to mutate an entry a pending send is reading.
 */
export class ResponseQueue<TPayload> {
  private constructor(private readonly entries: ReadonlyMap<string, QueuedSave<TPayload>>) {}

  static empty<TPayload>(): ResponseQueue<TPayload> {
    return new ResponseQueue<TPayload>(new Map());
  }

  /** Rehydrates a queue after a reload, preserving attempt counts so backoff is not reset. */
  static fromSnapshot<TPayload>(snapshot: QueueSnapshot<TPayload>): ResponseQueue<TPayload> {
    const entries = new Map<string, QueuedSave<TPayload>>();
    for (const [questionVersionId, value] of Object.entries(snapshot)) {
      if (!value || typeof value.payload === "undefined") {
        continue;
      }
      entries.set(questionVersionId, {
        questionVersionId,
        payload: value.payload,
        state: "Pending",
        attempts: Math.max(0, value.attempts ?? 0),
        nextAttemptAtMs: 0,
      });
    }
    return new ResponseQueue(entries);
  }

  toSnapshot(): QueueSnapshot<TPayload> {
    const snapshot: QueueSnapshot<TPayload> = {};
    for (const [key, entry] of this.entries) {
      snapshot[key] = { payload: entry.payload, attempts: entry.attempts };
    }
    return snapshot;
  }

  get size(): number {
    return this.entries.size;
  }

  has(questionVersionId: string): boolean {
    return this.entries.has(questionVersionId);
  }

  get(questionVersionId: string): QueuedSave<TPayload> | undefined {
    return this.entries.get(questionVersionId);
  }

  all(): QueuedSave<TPayload>[] {
    return Array.from(this.entries.values());
  }

  /**
   * Queues the latest value for a question. A new edit resets the attempt budget, because the payload
   * is now different from the one that failed and deserves a fresh try. The previous entry is
   * replaced, never appended (§77 one stream per attempt).
   */
  enqueue(questionVersionId: string, payload: TPayload, nowMs: number): ResponseQueue<TPayload> {
    const next = new Map(this.entries);
    next.set(questionVersionId, {
      questionVersionId,
      payload,
      state: "Pending",
      attempts: 0,
      nextAttemptAtMs: nowMs,
    });
    return new ResponseQueue(next);
  }

  /** Marks an entry as in flight so a second caller cannot send the same save concurrently. */
  markSending(questionVersionId: string): ResponseQueue<TPayload> {
    const entry = this.entries.get(questionVersionId);
    if (!entry) {
      return this;
    }
    const next = new Map(this.entries);
    next.set(questionVersionId, { ...entry, state: "Sending" });
    return new ResponseQueue(next);
  }

  /** The server acknowledged the save: the entry leaves the queue, which is the only path to "saved". */
  acknowledge(questionVersionId: string): ResponseQueue<TPayload> {
    if (!this.entries.has(questionVersionId)) {
      return this;
    }
    const next = new Map(this.entries);
    next.delete(questionVersionId);
    return new ResponseQueue(next);
  }

  /**
   * A send failed. The entry becomes {@link QueueEntryState.Failed} once the attempt budget is spent,
   * otherwise it returns to {@code Pending} with a backoff deadline. A failed entry stays in the queue
   * so the snapshot survives a reload and the UI can show it as unsaved (§78).
   */
  fail(questionVersionId: string, nowMs: number): ResponseQueue<TPayload> {
    const entry = this.entries.get(questionVersionId);
    if (!entry) {
      return this;
    }
    const attempts = entry.attempts + 1;
    const exhausted = attempts >= MAX_SAVE_ATTEMPTS;
    const next = new Map(this.entries);
    next.set(questionVersionId, {
      ...entry,
      attempts,
      state: exhausted ? "Failed" : "Pending",
      nextAttemptAtMs: exhausted ? Number.POSITIVE_INFINITY : nowMs + backoffDelayMs(attempts),
    });
    return new ResponseQueue(next);
  }

  /** Drops every entry without acknowledging it — used only after a submission is accepted (§133). */
  clear(): ResponseQueue<TPayload> {
    return ResponseQueue.empty<TPayload>();
  }

  /**
   * The entries due to be sent now: pending (not in flight) and past their backoff deadline. Bounded
   * by {@code limit} so a large offline backlog drains at a controlled rate rather than stampeding.
   */
  drain(nowMs: number, limit = 3): QueuedSave<TPayload>[] {
    return this.all()
      .filter((entry) => entry.state === "Pending" && entry.nextAttemptAtMs <= nowMs)
      .slice(0, Math.max(0, limit));
  }

  /** Entries that lost their budget; the UI must surface these as unsaved (§78). */
  failures(): QueuedSave<TPayload>[] {
    return this.all().filter((entry) => entry.state === "Failed");
  }
}

/**
 * Flushes the queue against an injected transport.
 *
 * <p>Used by the runtime's reconnect path and by the tests. It drains at most {@code batchSize}
 * entries per pass, so a student who made fifty edits offline does not fire fifty simultaneous
 * requests on reconnect (§77). Returns the new queue plus the ids that were acknowledged.
 */
export async function flushQueue<TPayload>(
  queue: ResponseQueue<TPayload>,
  send: (entry: QueuedSave<TPayload>) => Promise<void>,
  nowMs: number,
  batchSize = 3,
): Promise<{ queue: ResponseQueue<TPayload>; saved: string[] }> {
  let current = queue;
  const saved: string[] = [];
  for (const entry of queue.drain(nowMs, batchSize)) {
    current = current.markSending(entry.questionVersionId);
    try {
      await send(entry);
      current = current.acknowledge(entry.questionVersionId);
      saved.push(entry.questionVersionId);
    } catch {
      current = current.fail(entry.questionVersionId, nowMs);
    }
  }
  return { queue: current, saved };
}
