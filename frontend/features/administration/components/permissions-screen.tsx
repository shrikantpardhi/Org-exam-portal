"use client";

import { useState } from "react";

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
  const query = usePermissions(page, toSortParam(sort, "permissions"));

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
      <p className="text-sm text-slate-500">
        Permissions are read-only through this API. Role assignments are changed on the Roles screen
        with the explicit assignment permission.
      </p>
      <DataTable
        caption="Permissions"
        columns={columns}
        rows={query.data?.items ?? []}
        getRowKey={(permission) => permission.id}
        loading={query.isPending}
        error={query.isError ? describeAdminError(query.error, "Permissions could not be loaded.") : null}
        onRetry={() => {
          void query.refetch();
        }}
        emptyMessage="No permissions found."
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
