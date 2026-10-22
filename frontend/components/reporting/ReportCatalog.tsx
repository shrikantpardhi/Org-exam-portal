"use client";

import { useId, useMemo, useState } from "react";

import type { ReportCatalogEntry } from "@/features/reporting/schemas/reporting";

import { filterLabel } from "@/features/reporting/format";

/**
 * The report catalog browser ({@code specs/22-P13-Reporting-V1.5.md §11, §109-§111}).
 *
 * <p>Entries are advertised by the server, grouped by the frozen category contract ({@code §13}) and
 * filterable by category. An entry whose {@code enabled} flag is false is registered but has no data
 * provider behind it, so it is rendered as plainly unavailable and exposes no select control at all —
 * disabling a button alone would still imply it could work.
 */
export function ReportCatalog({
  entries,
  selectedType,
  onSelect,
}: {
  entries: ReportCatalogEntry[];
  selectedType: string | null;
  onSelect: (entry: ReportCatalogEntry) => void;
}) {
  const [category, setCategory] = useState("ALL");
  const headingId = useId();

  const categories = useMemo(
    () => Array.from(new Set(entries.map((entry) => entry.category))).sort(),
    [entries],
  );

  const visible = useMemo(
    () => (category === "ALL" ? entries : entries.filter((entry) => entry.category === category)),
    [entries, category],
  );

  const groups = useMemo(() => {
    const byCategory = new Map<string, ReportCatalogEntry[]>();
    for (const entry of visible) {
      const bucket = byCategory.get(entry.category) ?? [];
      bucket.push(entry);
      byCategory.set(entry.category, bucket);
    }
    return Array.from(byCategory.entries()).sort(([left], [right]) => left.localeCompare(right));
  }, [visible]);

  if (entries.length === 0) {
    return (
      <p role="note" className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-4 text-sm text-slate-700">
        No reports are available to you.
      </p>
    );
  }

  return (
    <section aria-labelledby={headingId} className="space-y-3">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id={headingId} className="text-lg font-semibold tracking-tight">
            Report catalog
          </h2>
          <p className="text-sm text-slate-500">
            Only reports the server advertises for your permissions are listed.
          </p>
        </div>
        <div className="space-y-1">
          <label htmlFor="report-category" className="text-sm font-medium">
            Category
          </label>
          <select
            id="report-category"
            value={category}
            onChange={(event) => setCategory(event.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm sm:w-56"
          >
            <option value="ALL">All categories</option>
            {categories.map((value) => (
              <option key={value} value={value}>
                {value.charAt(0) + value.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </div>
      </div>

      {visible.length === 0 ? (
        <p role="note" className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-4 text-sm text-slate-700">
          No reports exist in that category.
        </p>
      ) : null}

      <div className="space-y-4">
        {groups.map(([groupName, groupEntries]) => (
          <section key={groupName} aria-label={`${groupName} reports`} className="space-y-2">
            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
              {groupName.charAt(0) + groupName.slice(1).toLowerCase()}
            </h3>
            <ul className="grid gap-3 sm:grid-cols-2">
              {groupEntries.map((entry) => {
                const selected = entry.reportType === selectedType;
                const disabled = !entry.enabled;
                return (
                  <li
                    key={entry.reportType}
                    data-report-type={entry.reportType}
                    data-enabled={entry.enabled ? "true" : "false"}
                    aria-current={selected ? "true" : undefined}
                    className={`rounded-lg border p-4 ${
                      disabled
                        ? "border-dashed border-slate-300 bg-slate-50 text-slate-500"
                        : selected
                          ? "border-blue-500 bg-blue-50"
                          : "border-slate-200 bg-white"
                    }`}
                  >
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div className="space-y-1">
                        <h4 className="text-base font-semibold">{entry.displayName}</h4>
                        <p className="text-xs text-slate-500">{entry.reportType}</p>
                      </div>
                      <span className="rounded-full border border-slate-200 bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                        {entry.executionMode === "ASYNC_PREFERRED"
                          ? "Large reports are queued"
                          : entry.executionMode === "SYNC"
                            ? "Runs immediately"
                            : "Execution mode unknown"}
                      </span>
                    </div>

                    <p className="mt-2 text-sm text-slate-600">
                      {entry.description ?? "No description was provided for this report."}
                    </p>

                    <dl className="mt-3 grid grid-cols-2 gap-2 text-xs text-slate-500">
                      <div>
                        <dt className="font-medium text-slate-600">Formats</dt>
                        <dd>{entry.supportedFormats.join(", ")}</dd>
                      </div>
                      <div>
                        <dt className="font-medium text-slate-600">Filters</dt>
                        <dd>
                          {entry.filterSchema.length > 0
                            ? entry.filterSchema.map(filterLabel).join(", ")
                            : "None"}
                        </dd>
                      </div>
                    </dl>

                    <div className="mt-3">
                      {disabled ? (
                        <p role="note" className="text-xs font-medium text-slate-500">
                          Not available yet — this report type has no data provider.
                        </p>
                      ) : (
                        <button
                          type="button"
                          aria-pressed={selected}
                          onClick={() => onSelect(entry)}
                          className="rounded border border-slate-300 bg-white px-3 py-1 text-sm font-medium hover:border-blue-300 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                        >
                          {selected ? "Selected" : "Select"}
                        </button>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          </section>
        ))}
      </div>
    </section>
  );
}
