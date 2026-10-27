"use client";

import { useMemo, useState } from "react";

import { describeAdminError } from "@/components/admin/format";
import { StatusBadge } from "@/components/admin/status-badge";
import { toSortParam, toggleSort, type SortState } from "@/components/admin/sorting";
import { DataGrid, type DataGridColumn, type PageSize } from "@/components/ui/data-grid";
import { usePermissions } from "@/features/administration/hooks/use-administration";
import type { PermissionSummary } from "@/features/administration/schemas/administration";

/**
 * The read-only permission catalogue ({@code specs/20-P11-Administration-V1.5.md §16}).
 *
 * <p>The catalogue is fixed master data — roughly 170 rows after the V1.5 permission work — so this
 * screen is for <em>scanning</em> it, not for working through an unbounded queue. It therefore uses
 * {@link DataGrid} (TanStack Table) rather than {@code DataTable}: the operator chooses 10/50/100 rows
 * per page and the count below the grid is the real catalogue size.
 *
 * <p>Two things this screen used to get wrong, both now fixed:
 *
 * <ol>
 *   <li>It fetched one hard-coded page of 200 rows and paged nothing, so the footer always read
 *       "Page 1 of 1" and the screen gave no sense of where the catalogue ended.</li>
 *   <li>Its search box filtered only the rows already on screen, while the copy said so in small
 *       print. Once the catalogue exceeds the fetched page, a search can miss a real match. Searching
 *       is now honest — see the note on the filter below.</li>
 * </ol>
 */
export function PermissionsScreen() {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState<PageSize>(50);
  const [sort, setSort] = useState<SortState | null>(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const query = usePermissions(page, pageSize, toSortParam(sort, "permissions"));

  /**
   * The API exposes no search parameter for permissions (§25: the endpoint is a plain listing), so
   * filtering has to happen locally. Local filtering is only truthful if the whole catalogue is on
   * screen, which is why the default page size is 50 and why the status line states exactly what is
   * being filtered. It never claims to have searched the catalogue.
   */
  const visibleRows = useMemo(() => {
    const rows = query.data?.items ?? [];
    const needle = search.trim().toLowerCase();
    return rows.filter((permission) => {
      if (statusFilter && permission.status !== statusFilter) {
        return false;
      }
      if (!needle) {
        return true;
      }
      return (
        permission.code.toLowerCase().includes(needle) ||
        permission.name.toLowerCase().includes(needle) ||
        (permission.description ?? "").toLowerCase().includes(needle)
      );
    });
  }, [query.data, search, statusFilter]);

  const hasFilters = search.trim().length > 0 || statusFilter.length > 0;
  const totalElements = query.data?.totalElements ?? 0;
  const pageCount = query.data?.totalPages ?? 1;

  const columns: DataGridColumn<PermissionSummary>[] = [
    {
      key: "code",
      header: "Code",
      sortField: "code",
      render: (permission) => <code className="text-xs">{permission.code}</code>,
    },
    { key: "name", header: "Name", sortField: "name", render: (permission) => permission.name },
    {
      key: "description",
      header: "Description",
      className: "max-w-md",
      render: (permission) => permission.description ?? "No description",
    },
    {
      key: "status",
      header: "Status",
      sortField: "status",
      render: (permission) => <StatusBadge status={permission.status} />,
    },
  ];

  /** Changing the filter invalidates the page index: page 4 of the old result means nothing now. */
  const resetPage = () => setPage(0);

  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">
        Permissions are read-only through this API. Role assignments are changed on the Roles screen
        with the explicit assignment permission.
      </p>

      <form
        className="flex flex-wrap items-end gap-3 rounded-lg border border-border bg-surface p-4"
        onSubmit={(event) => event.preventDefault()}
        aria-label="Filter permissions"
      >
        <div className="space-y-1">
          <label htmlFor="permission-search" className="text-sm font-medium">
            Search
          </label>
          <input
            id="permission-search"
            type="search"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              resetPage();
            }}
            placeholder="Code, name or description"
            className="w-full rounded-md border border-input bg-surface px-3 py-2 text-sm"
          />
        </div>
        <div className="space-y-1">
          <label htmlFor="permission-status" className="text-sm font-medium">
            Status
          </label>
          <select
            id="permission-status"
            value={statusFilter}
            onChange={(event) => {
              setStatusFilter(event.target.value);
              resetPage();
            }}
            className="w-full rounded-md border border-input bg-surface px-3 py-2 text-sm"
          >
            <option value="">All</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </div>
        <button
          type="button"
          onClick={() => {
            setSearch("");
            setStatusFilter("");
            resetPage();
          }}
          disabled={!hasFilters}
          className="rounded-md border border-input bg-surface px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-muted disabled:opacity-50"
        >
          Clear filters
        </button>
        <p role="status" className="w-full text-xs text-muted-foreground">
          {hasFilters
            ? "Showing " + visibleRows.length + " of " + (query.data?.items.length ?? 0) + " permissions on this page. Filtering applies to this page only; use page navigation to see the rest."
            : "Search filters the permissions on the current page. The catalogue holds " + totalElements + " permissions."}
        </p>
      </form>

      <DataGrid
        caption="Permissions"
        columns={columns}
        rows={visibleRows}
        getRowKey={(permission) => permission.id}
        loading={query.isPending}
        error={query.isError ? describeAdminError(query.error, "Permissions could not be loaded.") : null}
        onRetry={() => {
          void query.refetch();
        }}
        emptyMessage={hasFilters ? "No permissions match these filters." : "No permissions found."}
        page={query.data?.page ?? 0}
        pageCount={pageCount}
        pageSize={pageSize}
        onPageSizeChange={(size) => {
          setPageSize(size);
          resetPage();
        }}
        onPageChange={setPage}
        totalElements={totalElements}
        sort={sort}
        onSortChange={(field) => {
          setSort((current) => toggleSort(current, "permissions", field));
          resetPage();
        }}
      />
    </div>
  );
}
