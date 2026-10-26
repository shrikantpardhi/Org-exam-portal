"use client";

import { useMemo, useState, type FormEvent } from "react";

import {
  isUuid,
  toIsoInstant,
  type ReportCatalogEntry,
  type ReportFormat,
  type ReportRequestInput,
} from "@/features/reporting/schemas/reporting";

import { filterLabel } from "@/features/reporting/format";

/** The first supported format, defaulting to CSV only when the catalog lists none. */
function initialFormat(value: string | undefined): ReportFormat {
  return value === "PDF" ? "PDF" : "CSV";
}

/**
 * The report request form ({@code specs/22-P13-Reporting-V1.5.md §14-§16, §112, §189}).
 *
 * <p>One input is rendered per filter key the catalog advertises. Identifier filters are UUID text
 * inputs, {@code from}/{@code to} are local date-time inputs converted to ISO-8601 instants, and the
 * remaining keys are plain text. Client validation only improves the experience: the backend
 * re-validates every key, value and range and remains authoritative ({@code §15}, {@code §189}).
 */
export function ReportFilterForm({
  entry,
  busy = false,
  onSubmit,
}: {
  entry: ReportCatalogEntry;
  busy?: boolean;
  onSubmit: (body: ReportRequestInput) => void;
}) {
  const [values, setValues] = useState<Record<string, string>>({});
  const [format, setFormat] = useState<ReportFormat>(() =>
    initialFormat(entry.supportedFormats[0]),
  );
  const [errors, setErrors] = useState<Record<string, string>>({});

  const idPrefix = useMemo(() => `report-filter-${entry.reportType}`, [entry.reportType]);
  const formats = entry.supportedFormats;

  const setValue = (key: string, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => {
      if (!(key in current)) {
        return current;
      }
      const next = { ...current };
      delete next[key];
      return next;
    });
  };

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const nextErrors: Record<string, string> = {};
    const filters: Record<string, string> = {};

    for (const key of entry.filterSchema) {
      const raw = (values[key] ?? "").trim();
      if (raw.length === 0) {
        continue;
      }
      if (key.endsWith("Id")) {
        if (!isUuid(raw)) {
          nextErrors[key] = "Enter a valid identifier";
          continue;
        }
        filters[key] = raw;
        continue;
      }
      if (key === "from" || key === "to") {
        const iso = toIsoInstant(raw);
        if (!iso) {
          nextErrors[key] = "Enter a valid date and time";
          continue;
        }
        filters[key] = iso;
        continue;
      }
      filters[key] = raw;
    }

    // A reversed range is rejected here only so the operator sees it immediately.
    const from = filters.from ? Date.parse(filters.from) : null;
    const to = filters.to ? Date.parse(filters.to) : null;
    if (from !== null && to !== null && to < from) {
      nextErrors.to = "The end must not precede the start";
    }

    if (!formats.includes(format)) {
      nextErrors.format = "Choose a supported format";
    }

    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      return;
    }

    setErrors({});
    onSubmit({ reportType: entry.reportType, filters, format });
  };

  return (
    <form
      aria-label={`Generate ${entry.displayName}`}
      onSubmit={submit}
      className="space-y-3 rounded-lg border border-border bg-surface p-4"
    >
      <div>
        <h2 className="text-lg font-semibold tracking-tight">{entry.displayName}</h2>
        <p className="text-sm text-muted-foreground">
          {entry.description ?? "No description was provided for this report."}
        </p>
      </div>

      {entry.filterSchema.length > 0 ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {entry.filterSchema.map((key) => {
            const id = `${idPrefix}-${key}`;
            const error = errors[key];
            const inputType =
              key === "from" || key === "to"
                ? "datetime-local"
                : key.endsWith("Id")
                  ? "text"
                  : "text";
            const placeholder = key.endsWith("Id") ? "UUID" : undefined;
            return (
              <div key={key} className="space-y-1">
                <label htmlFor={id} className="text-sm font-medium">
                  {filterLabel(key)}
                </label>
                <input
                  id={id}
                  type={inputType}
                  value={values[key] ?? ""}
                  placeholder={placeholder}
                  aria-invalid={error ? true : undefined}
                  aria-describedby={error ? `${id}-error` : undefined}
                  onChange={(event) => setValue(key, event.target.value)}
                  className={`w-full rounded-md border px-3 py-2 text-sm ${
                    error ? "border-red-400" : "border-input"
                  }`}
                />
                {error ? (
                  <p id={`${id}-error`} role="alert" className="text-xs text-red-600">
                    {error}
                  </p>
                ) : null}
              </div>
            );
          })}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">This report takes no filters.</p>
      )}

      <div className="space-y-1">
        <label htmlFor={`${idPrefix}-format`} className="text-sm font-medium">
          Format
        </label>
        <select
          id={`${idPrefix}-format`}
          value={format}
          onChange={(event) => setFormat(event.target.value === "PDF" ? "PDF" : "CSV")}
          className="w-full rounded-md border border-input px-3 py-2 text-sm sm:w-48"
        >
          {formats.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
        {errors.format ? (
          <p role="alert" className="text-xs text-red-600">
            {errors.format}
          </p>
        ) : null}
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="submit"
          disabled={busy}
          className="rounded bg-primary px-4 py-2 text-sm font-medium text-white disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-900"
        >
          {busy ? "Generating…" : "Generate report"}
        </button>
        <p className="text-xs text-muted-foreground">
          Small reports return immediately; large reports are queued and polled until complete.
        </p>
      </div>
    </form>
  );
}
