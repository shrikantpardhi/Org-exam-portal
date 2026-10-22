import { describe, expect, it } from "vitest";

import {
  BASE_BACKOFF_MS,
  MAX_BACKOFF_MS,
  MAX_SAVE_ATTEMPTS,
  ResponseQueue,
  backoffDelayMs,
  flushQueue,
  type QueuedSave,
} from "@/features/delivery/lib/response-queue";

/**
 * Offline queue tests ({@code specs/14-P5-Delivery-V1.5.md §77, §78}).
 *
 * <p>Two properties matter most and are asserted directly: the queue holds one pending save per
 * question (latest value wins, never an append-only log), and an entry is reported as saved only after
 * the transport acknowledged it. A failed save must never masquerade as committed.
 */
describe("backoff", () => {
  it("grows exponentially from the base delay", () => {
    expect(backoffDelayMs(1)).toBe(BASE_BACKOFF_MS);
    expect(backoffDelayMs(2)).toBe(2 * BASE_BACKOFF_MS);
    expect(backoffDelayMs(3)).toBe(4 * BASE_BACKOFF_MS);
  });

  it("is capped so a long outage does not schedule an absurd delay", () => {
    expect(backoffDelayMs(20)).toBe(MAX_BACKOFF_MS);
  });
});

describe("one stream per question", () => {
  it("replaces the queued payload instead of appending when the student edits again", () => {
    let queue = ResponseQueue.empty<string>();
    queue = queue.enqueue("qv-1", "A", 0);
    queue = queue.enqueue("qv-1", "B", 0);
    queue = queue.enqueue("qv-1", "C", 0);

    expect(queue.size).toBe(1);
    expect(queue.get("qv-1")?.payload).toBe("C");
  });

  it("keeps distinct questions as distinct entries", () => {
    let queue = ResponseQueue.empty<string>();
    queue = queue.enqueue("qv-1", "A", 0);
    queue = queue.enqueue("qv-2", "B", 0);
    expect(queue.size).toBe(2);
  });

  it("resets the attempt budget on a fresh edit, because the payload changed", () => {
    let queue = ResponseQueue.empty<string>();
    queue = queue.enqueue("qv-1", "A", 0);
    queue = queue.fail("qv-1", 0);
    expect(queue.get("qv-1")?.attempts).toBe(1);
    queue = queue.enqueue("qv-1", "B", 100);
    expect(queue.get("qv-1")?.attempts).toBe(0);
  });
});

describe("acknowledgement is the only path to saved", () => {
  it("removes the entry when the server acknowledges it", () => {
    let queue = ResponseQueue.empty<string>();
    queue = queue.enqueue("qv-1", "A", 0);
    queue = queue.acknowledge("qv-1");
    expect(queue.size).toBe(0);
    expect(queue.failures()).toHaveLength(0);
  });

  it("keeps a failed entry queued and counts the attempt", () => {
    let queue = ResponseQueue.empty<string>();
    queue = queue.enqueue("qv-1", "A", 0);
    queue = queue.fail("qv-1", 0);
    expect(queue.size).toBe(1);
    expect(queue.get("qv-1")?.state).toBe("Pending");
    expect(queue.get("qv-1")?.attempts).toBe(1);
  });

  it("marks the entry failed — never saved — once the attempt budget is exhausted", () => {
    let queue = ResponseQueue.empty<string>();
    queue = queue.enqueue("qv-1", "A", 0);
    for (let attempt = 0; attempt < MAX_SAVE_ATTEMPTS; attempt += 1) {
      queue = queue.fail("qv-1", 0);
    }
    expect(queue.get("qv-1")?.state).toBe("Failed");
    expect(queue.failures()).toHaveLength(1);
    expect(queue.size).toBe(1);
  });

  it("does not redeliver a failed entry on drain, so the UI must surface it", () => {
    let queue = ResponseQueue.empty<string>();
    queue = queue.enqueue("qv-1", "A", 0);
    for (let attempt = 0; attempt < MAX_SAVE_ATTEMPTS; attempt += 1) {
      queue = queue.fail("qv-1", 0);
    }
    expect(queue.drain(Number.MAX_SAFE_INTEGER)).toHaveLength(0);
  });
});

describe("drain respects backoff and batch size", () => {
  it("does not send before the backoff deadline", () => {
    let queue = ResponseQueue.empty<string>();
    queue = queue.enqueue("qv-1", "A", 0);
    queue = queue.fail("qv-1", 1_000);
    // next attempt is due at 1000 + BASE_BACKOFF_MS.
    expect(queue.drain(1_000)).toHaveLength(0);
    expect(queue.drain(1_000 + BASE_BACKOFF_MS)).toHaveLength(1);
  });

  it("drains at most the requested batch size", () => {
    let queue = ResponseQueue.empty<string>();
    for (let index = 0; index < 10; index += 1) {
      queue = queue.enqueue(`qv-${index}`, "A", 0);
    }
    expect(queue.drain(0, 3)).toHaveLength(3);
  });

  it("never drains an entry that is already in flight", () => {
    let queue = ResponseQueue.empty<string>();
    queue = queue.enqueue("qv-1", "A", 0);
    queue = queue.markSending("qv-1");
    expect(queue.drain(0)).toHaveLength(0);
  });
});

describe("snapshot survival across a reload", () => {
  it("round-trips pending payloads and attempt counts", () => {
    let queue = ResponseQueue.empty<string>();
    queue = queue.enqueue("qv-1", "A", 0);
    queue = queue.fail("qv-1", 0);
    const restored = ResponseQueue.fromSnapshot<string>(queue.toSnapshot());
    expect(restored.size).toBe(1);
    expect(restored.get("qv-1")?.payload).toBe("A");
    expect(restored.get("qv-1")?.attempts).toBe(1);
    expect(restored.get("qv-1")?.state).toBe("Pending");
  });

  it("ignores malformed snapshot entries rather than throwing", () => {
    const restored = ResponseQueue.fromSnapshot<string>({
      broken: undefined as unknown as { payload: string; attempts: number },
      good: { payload: "A", attempts: 0 },
    });
    expect(restored.size).toBe(1);
    expect(restored.get("good")?.payload).toBe("A");
  });
});

describe("flushQueue", () => {
  it("acknowledges each entry only after the transport resolves", async () => {
    let queue = ResponseQueue.empty<string>();
    queue = queue.enqueue("qv-1", "A", 0);
    queue = queue.enqueue("qv-2", "B", 0);
    const sent: string[] = [];
    const result = await flushQueue(queue, async (entry: QueuedSave<string>) => {
      sent.push(entry.questionVersionId);
    }, 0);
    expect(sent).toHaveLength(2);
    expect(result.queue.size).toBe(0);
    expect(result.saved).toEqual(["qv-1", "qv-2"]);
  });

  it("keeps an entry queued when the transport rejects it", async () => {
    let queue = ResponseQueue.empty<string>();
    queue = queue.enqueue("qv-1", "A", 0);
    const result = await flushQueue(queue, async () => {
      throw new Error("network down");
    }, 0);
    expect(result.queue.size).toBe(1);
    expect(result.saved).toHaveLength(0);
    expect(result.queue.get("qv-1")?.attempts).toBe(1);
  });

  it("sends a bounded batch per pass so reconnect does not stampede", async () => {
    let queue = ResponseQueue.empty<string>();
    for (let index = 0; index < 9; index += 1) {
      queue = queue.enqueue(`qv-${index}`, "A", 0);
    }
    const result = await flushQueue(queue, async () => undefined, 0, 3);
    expect(result.saved).toHaveLength(3);
    expect(result.queue.size).toBe(6);
  });
});
