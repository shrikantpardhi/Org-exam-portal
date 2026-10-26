"use client";

import { describeAdminError } from "@/components/admin/format";
import { DataTable, PaginationBar, type DataTableColumn } from "@/components/admin/data-table";
import { toSortParam, toggleSort, type SortState } from "@/components/admin/sorting";
import { StatusBadge } from "@/components/admin/status-badge";
import { useState } from "react";
import {
 useLookups,
 usePermissions,
 useRoles,
} from "@/features/administration/hooks/use-administration";
import type { Lookup, PermissionSummary, RoleSummary } from "@/features/administration/schemas/administration";

/** Read-only role catalogue (super-admin-flow §12). */
export function SeedsRolesScreen() {
  const [sort, setSort] = useState<SortState | null>(null);
 const query = useRoles(true, toSortParam(sort, "roles"));

  const columns: DataTableColumn<RoleSummary>[] = [
 {
 key: "code",
 header: "Code",
 sortField: "code",
      render: (role) => <code className="text-xs">{role.code}</code>,
 },
 { key: "name", header: "Name", sortField: "name", render: (role) => role.name },
 { key: "description", header: "Description", render: (role) => role.description ?? "No description" },
 { key: "status", header: "Status", sortField: "status", render: (role) => <StatusBadge status={role.status} /> },
  ];

  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">The seeded role catalogue is read-only from the platform.</p>
      <DataTable
 caption="Roles"
        columns={columns}
        rows={query.data?.items ?? []}
        getRowKey={(role) => role.id}
 loading={query.isPending}
 error={query.isError ? describeAdminError(query.error, "Roles could not be loaded.") : null}
        onRetry={() => void query.refetch()}
 emptyMessage="No roles found."
        sort={sort}
        onSortChange={(field) => setSort((current) => toggleSort(current, "roles", field))}
      />
    </div>
  );
}

/** Read-only permission catalogue (super-admin-flow §13). */
export function SeedsPermissionsScreen() {
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
 { key: "status", header: "Status", sortField: "status", render: (permission) => <StatusBadge status={permission.status} /> },
  ];

  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">The permission catalogue is read-only from the platform.</p>
      <DataTable
 caption="Permissions"
        columns={columns}
        rows={query.data?.items ?? []}
        getRowKey={(permission) => permission.id}
 loading={query.isPending}
 error={query.isError ? describeAdminError(query.error, "Permissions could not be loaded.") : null}
        onRetry={() => void query.refetch()}
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

/** Read-only lookup master values (super-admin-flow §14). */
export function SeedsLookupsScreen() {
 const [category, setCategory] = useState("");
  const query = useLookups({ category: category || undefined });

  const columns: DataTableColumn<Lookup>[] = [
 { key: "category", header: "Category", render: (lookup) => lookup.category },
 {
 key: "code",
 header: "Code",
      render: (lookup) => <code className="text-xs">{lookup.code}</code>,
 },
 { key: "name", header: "Name", render: (lookup) => lookup.name },
 { key: "displayOrder", header: "Order", render: (lookup) => lookup.displayOrder ?? "—" },
 { key: "status", header: "Status", render: (lookup) => <StatusBadge status={lookup.status} /> },
  ];

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <input
 aria-label="Category"
          value={category}
          onChange={(event) => setCategory(event.target.value)}
 placeholder="Filter by category"
 className="w-full max-w-xs rounded-md border border-input px-3 py-2 text-sm"
        />
      </div>
      <DataTable
 caption="Lookups"
        columns={columns}
        rows={query.data ?? []}
        getRowKey={(lookup) => lookup.id}
 loading={query.isPending}
 error={query.isError ? describeAdminError(query.error, "Lookups could not be loaded.") : null}
        onRetry={() => void query.refetch()}
 emptyMessage="No lookups found."
      />
    </div>
  );
}
