"use client";

import Link from "next/link";
import { useState } from "react";

import { describeAdminError } from "@/components/admin/format";
import { DataTable, PaginationBar, type DataTableColumn } from "@/components/admin/data-table";
import { toSortParam, toggleSort, type SortState } from "@/components/admin/sorting";
import { StatusBadge } from "@/components/admin/status-badge";
import { useOrganizations } from "@/features/platform/hooks/use-platform";
import type { OrganizationSummary } from "@/features/platform/schemas/platform";

/** Business organizations list (super-admin-flow §5). PLATFORM is excluded by the backend. */
export function OrganizationsScreen() {
 const [status, setStatus] = useState("");
 const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState | null>(null);

 const query = useOrganizations({
 status: status || undefined,
 search: search || undefined,
 sort: toSortParam(sort, "organizations"),
    page,
    size: 20,
  });

  const columns: DataTableColumn<OrganizationSummary>[] = [
 {
 key: "code",
 header: "Code",
 sortField: "code",
      render: (org) => <code className="text-xs">{org.code}</code>,
 },
 { key: "name", header: "Name", sortField: "name", render: (org) => org.name },
 {
 key: "status",
 header: "Status",
 sortField: "status",
      render: (org) => <StatusBadge status={org.status} />,
 },
 {
 key: "actions",
 header: "",
      render: (org) => (
        <Link
 href={"/platform/organizations/" + org.id}
 className="font-medium text-blue-600 hover:underline"
        >
          View
        </Link>
      ),
    },
  ];

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <select
 aria-label="Status"
          value={status}
          onChange={(event) => {
 setStatus(event.target.value);
 setPage(0);
 }}
 className="rounded-md border border-input px-3 py-2 text-sm"
        >
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
        <input
 aria-label="Search"
          value={search}
          onChange={(event) => {
 setSearch(event.target.value);
 setPage(0);
 }}
 placeholder="Search code or name"
 className="w-full max-w-xs rounded-md border border-input px-3 py-2 text-sm"
        />
      </div>

      <DataTable
 caption="Organizations"
        columns={columns}
        rows={query.data?.items ?? []}
        getRowKey={(org) => org.id}
 loading={query.isPending}
 error={query.isError ? describeAdminError(query.error, "Organizations could not be loaded.") : null}
        onRetry={() => {
 void query.refetch();
 }}
 emptyMessage="No organizations found."
        sort={sort}
        onSortChange={(field) => {
          setSort((current) => toggleSort(current, "organizations", field));
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
