"use client";

import { useMemo, useState } from "react";

import { DataTable, PaginationBar, type DataTableColumn } from "@/components/admin/data-table";
import { StatusBadge } from "@/components/admin/status-badge";
import { describeAdminError } from "@/components/admin/format";
import { toSortParam, toggleSort, type SortState } from "@/components/admin/sorting";
import { usePermissions } from "@/features/administration/hooks/use-administration";
import type { PermissionSummary } from "@/features/administration/schemas/administration";

/** The read-only permission catalogue ({@code specs/20-P11-Administration-V1.5.md §16}). */
export function PermissionsScreen() {
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState | null>(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const query = usePermissions(page, toSortParam(sort, "permissions"));

  /**
   * The permission catalogue is a fixed, small list served one page at a time, and the API exposes no
   * search parameter. Filtering the current page locally is therefore the honest behaviour: it narrows
   * what is on screen without pretending to have searched the whole catalogue. The copy says so.
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

  const columns: DataTableColumn<PermissionSummary>[] = [
    {
      key: "code",
      header: "Code",
      sortField: "code",
      render: (permission) => <code className="text-xs">{permission.code}</code>,
    },
    { key: "name", header: "Name", sortField: "name", render: (permission) => permission.name },
    { key: "description", header: "Description", render: (permission) => permission.description ?? "No description" },
    {
      key: "status",
      header: "Status",
      sortField: "status",
      render: (permission) => <StatusBadge status={permission.status} />,
    },
  ];

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
            onChange={(event) => setSearch(event.target.value)}
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
            onChange={(event) => setStatusFilter(event.target.value)}
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
          }}
          disabled={!hasFilters}
          className="rounded-md border border-input bg-surface px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-muted disabled:opacity-50"
        >
          Clear filters
        </button>
        <p role="status" className="w-full text-xs text-muted-foreground">
          {hasFilters
            ? "Showing " + visibleRows.length + " of " + (query.data?.items.length ?? 0) + " permissions on this page."
            : "Filters apply to the permissions on the current page."}
        </p>
      </form>
      <DataTable
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
        sort={sort}
        onSortChange={(field) => {
          setSort((current) => toggleSort(current, "permissions", field));
          setPage(0);
        }}
        footer={
          <PaginationBar
            page={query.data?.page ?? 0}
            totalPages={query.data?.totalPages ?? 1}
            onPageChange={setPage}
          />
        }
      />
    </div>
  );
}
