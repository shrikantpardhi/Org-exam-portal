"use client";

/**
 * Rank badge ({@code specs/15-P6-Evaluation-V1.5.md §35, §37}).
 *
 * <p>A missing rank is rendered as "Unranked", never as rank 0 — an unranked result has not been
 * compared, which is not the same as finishing last ({@code §37}).
 */
export function RankBadge({ rank }: { rank: number | null }) {
  const hasRank = rank !== null && Number.isFinite(rank) && rank > 0;

  if (!hasRank) {
    return (
      <span className="rounded-full border border-border bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
        Unranked
      </span>
    );
  }

  const leader = rank <= 3;
  return (
    <span
      aria-label={`Rank ${rank}`}
      className={`rounded-full border px-2 py-0.5 text-xs font-semibold tabular-nums ${
        leader
          ? "border-info bg-info-subtle text-info"
          : "border-border bg-surface text-foreground"
      }`}
    >
      #{rank}
    </span>
  );
}
