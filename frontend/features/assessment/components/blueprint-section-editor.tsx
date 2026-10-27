"use client";

/**
 * Blueprint sections editor ({@code specs/13-P4-Assessment-V1.5.md §12-§17, §99}).
 *
 * <p>Section names are free-text user labels (§13) — the platform ships no section catalog, so the
 * editor never offers a fixed list and never branches on an exam name. Reordering rewrites
 * {@code displayOrder} to an ascending 1..n sequence while keeping the typed names untouched.
 */
import { Button } from "@/components/ui/button";
import {
  newSectionRow,
  normalizeSectionOrders,
  type BlueprintSectionFormValue,
} from "@/features/assessment/schemas/blueprint-config";

const fieldClass =
  "w-full rounded-md border border-input px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

export function BlueprintSectionEditor({
  sections,
  onChange,
  disabled = false,
}: {
  sections: BlueprintSectionFormValue[];
  onChange: (sections: BlueprintSectionFormValue[]) => void;
  disabled?: boolean;
}) {
  function update(index: number, patch: Partial<BlueprintSectionFormValue>) {
    onChange(sections.map((section, at) => (at === index ? { ...section, ...patch } : section)));
  }

  function addSection() {
    onChange(normalizeSectionOrders([...sections, newSectionRow(sections.length + 1)]));
  }

  function removeSection(index: number) {
    onChange(normalizeSectionOrders(sections.filter((_, at) => at !== index)));
  }

  function move(index: number, direction: -1 | 1) {
    const target = index + direction;
    if (target < 0 || target >= sections.length) {
      return;
    }
    const next = [...sections];
    const [moved] = next.splice(index, 1);
    if (!moved) {
      return;
    }
    next.splice(target, 0, moved);
    onChange(normalizeSectionOrders(next));
  }

  return (
    <section aria-labelledby="blueprint-sections-heading" className="space-y-3">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="blueprint-sections-heading" className="text-lg font-semibold tracking-tight">
            Sections
          </h2>
          <p className="text-sm text-muted-foreground">
            Names are free text and are shown to candidates exactly as typed.
          </p>
        </div>
        <Button variant="outline" onClick={addSection} disabled={disabled}>
          Add section
        </Button>
      </div>

      {sections.length === 0 ? (
        <p role="note" className="rounded border border-dashed border-input bg-muted p-4 text-sm">
          No sections yet. A blueprint needs at least one section before publication.
        </p>
      ) : null}

      <ol className="space-y-3">
        {sections.map((section, index) => (
          <li
            key={section.key}
            data-testid="blueprint-section-row"
            className="rounded-lg border border-border bg-surface p-4 shadow-sm"
          >
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-6">
              <div className="lg:col-span-2">
                <label htmlFor={`section-name-${section.key}`} className="text-sm font-medium">
                  Name
                </label>
                <input
                  id={`section-name-${section.key}`}
                  aria-label={`Section ${index + 1} name`}
                  value={section.name}
                  disabled={disabled}
                  onChange={(event) => update(index, { name: event.target.value })}
                  className={fieldClass}
                />
              </div>
              <div>
                <label htmlFor={`section-order-${section.key}`} className="text-sm font-medium">
                  Order
                </label>
                <input
                  id={`section-order-${section.key}`}
                  aria-label={`Section ${index + 1} display order`}
                  type="number"
                  min={1}
                  value={section.displayOrder}
                  disabled={disabled}
                  onChange={(event) => update(index, { displayOrder: Number(event.target.value) })}
                  className={fieldClass}
                />
              </div>
              <div>
                <label htmlFor={`section-count-${section.key}`} className="text-sm font-medium">
                  Questions
                </label>
                <input
                  id={`section-count-${section.key}`}
                  aria-label={`Section ${index + 1} question count`}
                  type="number"
                  min={0}
                  value={section.questionCount}
                  disabled={disabled}
                  onChange={(event) => update(index, { questionCount: Number(event.target.value) })}
                  className={fieldClass}
                />
              </div>
              <div>
                <label htmlFor={`section-marks-${section.key}`} className="text-sm font-medium">
                  Marks
                </label>
                <input
                  id={`section-marks-${section.key}`}
                  aria-label={`Section ${index + 1} marks`}
                  type="number"
                  min={0}
                  step="0.01"
                  value={section.marks}
                  disabled={disabled}
                  onChange={(event) => update(index, { marks: Number(event.target.value) })}
                  className={fieldClass}
                />
              </div>
              <div>
                <label htmlFor={`section-duration-${section.key}`} className="text-sm font-medium">
                  Minutes
                </label>
                <input
                  id={`section-duration-${section.key}`}
                  aria-label={`Section ${index + 1} duration minutes`}
                  type="number"
                  min={0}
                  value={section.durationMinutes}
                  disabled={disabled}
                  onChange={(event) => update(index, { durationMinutes: Number(event.target.value) })}
                  className={fieldClass}
                />
              </div>
            </div>

            <div className="mt-3 flex flex-wrap gap-2">
              <Button
                variant="outline"
                size="sm"
                aria-label={`Move section ${index + 1} up`}
                disabled={disabled || index === 0}
                onClick={() => move(index, -1)}
              >
                Move up
              </Button>
              <Button
                variant="outline"
                size="sm"
                aria-label={`Move section ${index + 1} down`}
                disabled={disabled || index === sections.length - 1}
                onClick={() => move(index, 1)}
              >
                Move down
              </Button>
              <Button
                variant="ghost"
                size="sm"
                aria-label={`Remove section ${index + 1}`}
                disabled={disabled}
                onClick={() => removeSection(index)}
              >
                Remove
              </Button>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
