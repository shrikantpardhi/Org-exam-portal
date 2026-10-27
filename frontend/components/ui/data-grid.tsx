"use client";

import type { ReactNode } from "react";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
} from "@tanstack/react-table";

export interface DataGridColumn<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  className?: string;
  /** An allow-listed server sort field. Omit to render a plain, non-sortable header. */
  sortField?: string;
}

export const PAGE_SIZE_OPTIONS = [10, 50, 100] as const;
export type PageSize = (typeof PAGE_SIZE_OPTIONS)[number];

/**
 * A server-paginated grid built on TanStack Table.
 *
 * <p>Why TanStack Table when {@link import("./data-table").DataTable} already exists: the existing
 * table renders a fixed column list, which is all most screens need, and it is deliberately kept so
 * those twenty-odd screens do not change. A grid is needed where the *operator* controls paging —
 * choosing 10, 50 or 100 rows — and where the column model has to be a real object rather than a
 * render function per cell. TanStack supplies exactly that column/paging model; it does not own the
 * markup, so the table still renders semantic `<table>`, `<th scope="col">` and `aria-sort`.
 *
 * <p>Paging is server-side throughout. The row count is the page the server returned, not a
 * client-side slice of everything, because these lists are unbounded ({@code specs/06 §44}).
 */
export function DataGrid<T>({
  caption,
  columns,
  rows,
  getRowKey,
  loading = false,
  error,
  onRetry,
  emptyMessage,
  page,
  pageCount,
  pageSize,
  onPageSizeChange,
  onPageChange,
  totalElements,
  sort,
  onSortChange,
}: {
  caption: string;
  columns: DataGridColumn<T>[];
  rows: T[];
  getRowKey: (row: T) => string;
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  emptyMessage: string;
  /** Zero-based current page, as the API reports it. */
  page: number;
  pageCount: number;
  pageSize: PageSize;
  onPageSizeChange: (size: PageSize) => void;
  onPageChange: (page: number) => void;
  totalElements: number;
  sort?: { field: string; direction: "asc" | "desc" } | null;
  onSortChange?: (field: string) => void;
}) {
  const columnDefs: ColumnDef<T>[] = columns.map((column) => ({
    id: column.key,
    header: column.header,
    cell: (context) => column.render(context.row.original),
  }));

  const sorting: SortingState = sort ? [{ id: sort.field, desc: sort.direction === "desc" }] : [];
  const table = useReactTable({
    data: rows,
    columns: columnDefs,
    getCoreRowModel: getCoreRowModel(),
    getRowId: getRowKey,
    // Server-side only: manual paging and sorting mean TanStack does not slice or reorder the page
    // the server already shaped.
    manualPagination: true,
    manualSorting: true,
    pageCount,
    state: { sorting },
  });

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
            className="mt-2 rounded border border-destructive bg-surface px-3 py-1 text-sm font-medium text-destructive focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-destructive"
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

  const lastPage = Math.max(pageCount - 1, 0);
  const firstRowNumber = page * pageSize + 1;
  const lastRowNumber = Math.min(page * pageSize + rows.length, totalElements);

  return (
    <div className="space-y-3">
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <caption className="sr-only">{caption}</caption>
          <thead>
            {table.getHeaderGroups().map((headerGroup) => (
              <tr key={headerGroup.id} className="border-b border-border">
                {headerGroup.headers.map((header) => {
                  const column = columns.find((candidate) => candidate.key === header.column.id);
                  const sortable = Boolean(column?.sortField) && Boolean(onSortChange);
                  const active = sort && sort.field === column?.sortField ? sort : null;
                  return (
                    <th
                      key={header.id}
                      scope="col"
                      aria-sort={
                        sortable ? (active ? (active.direction === "asc" ? "ascending" : "descending") : "none") : undefined
                      }
                      className={"py-2 pr-3 " + (column?.className ?? "")}
                    >
                      {sortable ? (
                        <button
                          type="button"
                          onClick={() => onSortChange?.(column?.sortField ?? "")}
                          className="inline-flex items-center gap-1 font-medium hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                        >
                          {flexRender(header.column.columnDef.header, header.getContext())}
                          <span aria-hidden="true">{active ? (active.direction === "asc" ? "▲" : "▼") : "↕"}</span>
                        </button>
                      ) : (
                        flexRender(header.column.columnDef.header, header.getContext())
                      )}
                    </th>
                  );
                })}
              </tr>
            ))}
          </thead>
          <tbody>
            {table.getRowModel().rows.map((row) => (
              <tr key={row.id} className="border-b border-border last:border-0">
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id} className="py-2 pr-3 align-top">
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
        <div className="flex items-center gap-2">
          <label htmlFor="grid-page-size" className="text-muted-foreground">
            Rows per page
          </label>
          <select
            id="grid-page-size"
            value={pageSize}
            onChange={(event) => onPageSizeChange(Number(event.target.value) as PageSize)}
            className="rounded border border-input bg-surface px-2 py-1 text-foreground focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
          >
            {PAGE_SIZE_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </div>

        <p className="text-muted-foreground">
          {totalElements === 0
            ? "No rows"
            : "Showing " + firstRowNumber + "–" + lastRowNumber + " of " + totalElements}
        </p>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => onPageChange(Math.max(page - 1, 0))}
            disabled={page <= 0}
            className="rounded border border-input bg-surface px-3 py-1 font-medium text-foreground disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
          >
            Previous
          </button>
          <span className="text-muted-foreground" aria-live="polite">
            Page {page + 1} of {lastPage + 1}
          </span>
          <button
            type="button"
            onClick={() => onPageChange(Math.min(page + 1, lastPage))}
            disabled={page >= lastPage}
            className="rounded border border-input bg-surface px-3 py-1 font-medium text-foreground disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
          >
            Next
          </button>
        </div>
      </div>
    </div>
  );
}
