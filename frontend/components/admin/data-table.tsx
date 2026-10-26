"use client";

import type { ReactNode } from "react";

import { ariaSortFor, type SortState } from "./sorting";

/**
 * A server-paginated admin table ({@code specs/20-P11-Administration-V1.5.md §65, §123}).
 *
 * <p>Loading, empty and error states are part of the table, and column headers are real table
 * headers so the data is navigable with a screen reader.
 *
 * <p>Sorting is server-side. A column is sortable only when it sets {@code sortField} to a field the
 * owning endpoint allow-lists; the table renders that header as a button and exposes
 * {@code aria-sort} for assistive technology.
 */
export interface DataTableColumn<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  className?: string;
  /** An allow-listed server sort field. Omit to render a plain, non-sortable header. */
  sortField?: string;
}

export function DataTable<T>({
  caption,
  columns,
  rows,
  getRowKey,
  loading = false,
  error,
  onRetry,
  emptyMessage,
  footer,
  sort,
  onSortChange,
}: {
  caption: string;
  columns: DataTableColumn<T>[];
  rows: T[];
  getRowKey: (row: T) => string;
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  emptyMessage: string;
  footer?: ReactNode;
  /** The active server sort, when the screen supports sorting ({@code §65}). */
  sort?: SortState | null;
  /** Called with the clicked field; the screen applies the allow-list through {@code toggleSort}. */
  onSortChange?: (field: string) => void;
}) {
  if (loading) {
    return (
      <p role="status" aria-live="polite" className="text-sm text-muted-foreground">
        Loading…
      </p>
    );
  }

  if (error) {
    return (
      <div role="alert" className="rounded-lg border border-destructive bg-destructive-subtle p-4 text-sm text-destructive">
        <p>{error}</p>
        {onRetry ? (
          <button
            type="button"
            onClick={onRetry}
            className="mt-2 rounded border border-destructive bg-surface px-3 py-1 text-sm font-medium text-destructive focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600"
          >
            Try again
          </button>
        ) : null}
      </div>
    );
  }

  if (rows.length === 0) {
    return (
      <p role="note" className="rounded-lg border border-dashed border-input bg-muted p-4 text-sm text-foreground">
        {emptyMessage}
      </p>
    );
  }

  return (
    <div className="space-y-3">
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <caption className="sr-only">{caption}</caption>
          <thead>
            <tr className="border-b border-border">
              {columns.map((column) => {
                const sortable = Boolean(column.sortField) && Boolean(onSortChange);
                const active = sort && sort.field === column.sortField ? sort : null;
                return (
                  <th
                    key={column.key}
                    scope="col"
                    aria-sort={sortable ? ariaSortFor(sort, column.sortField ?? "") : undefined}
                    className={`py-2 pr-3 ${column.className ?? ""}`}
                  >
                    {sortable ? (
                      <button
                        type="button"
                        onClick={() => onSortChange?.(column.sortField ?? "")}
                        className="inline-flex items-center gap-1 font-medium hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                      >
                        {column.header}
                        <span aria-hidden="true" className="text-xs text-muted-foreground">
                          {active ? (active.direction === "asc" ? "▲" : "▼") : "↕"}
                        </span>
                      </button>
                    ) : (
                      column.header
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={getRowKey(row)} className="border-b border-border align-top">
                {columns.map((column) => (
                  <td key={column.key} className={`py-2 pr-3 ${column.className ?? ""}`}>
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {footer}
    </div>
  );
}

/** Server-side page navigation ({@code §65}). */
export function PaginationBar({
  page,
  totalPages,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}) {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <nav aria-label="Pagination" className="flex flex-wrap items-center gap-3 text-sm">
      <button
        type="button"
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
        className="rounded border border-input bg-surface px-3 py-1 disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        Previous
      </button>
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
        className="rounded border border-input bg-surface px-3 py-1 disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        Next
      </button>
    </nav>
  );
}
