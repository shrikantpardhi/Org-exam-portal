"use client";

import { Button } from "@/components/ui/button";

/**
 * The section navigator ({@code specs/14-P5-Delivery-V1.5.md §131}).
 *
 * <p>Sections come from the questions the server returned, ordered by the configuration's
 * {@code sectionOrder}. Clicking a section jumps to its first question. It is presentation-only: it
 * does not enforce section policy, which the backend owns.
 */
export function SectionNavigator({
  sections,
  currentQuestionId,
  onJump,
}: {
  sections: readonly { section: string; ids: readonly string[] }[];
  currentQuestionId: string;
  onJump: (questionVersionId: string) => void;
}) {
  return (
    <nav aria-label="Sections" className="flex flex-wrap gap-2">
      {sections.map(({ section, ids }) => {
        const active = ids.includes(currentQuestionId);
        return (
          <Button
            key={section}
            variant="outline"
            size="sm"
            aria-current={active ? "true" : undefined}
            className={active ? "border-slate-900 bg-slate-100" : ""}
            onClick={() => {
              const first = ids[0];
              if (first) {
                onJump(first);
              }
            }}
          >
            {section}
            <span className="text-xs text-slate-500">({ids.length})</span>
          </Button>
        );
      })}
    </nav>
  );
}
