"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";

import { DangerActionDialog } from "@/components/admin/danger-action-dialog";
import { DataTable, PaginationBar, type DataTableColumn } from "@/components/admin/data-table";
import { FilterBar } from "@/components/admin/filter-bar";
import { StatusBadge } from "@/components/admin/status-badge";
import { describeAdminError } from "@/components/admin/format";
import { toSortParam, toggleSort, type SortState } from "@/components/admin/sorting";
import { useArchiveUser, useRoles, useUsers } from "@/features/administration/hooks/use-administration";
import type { UserSummary } from "@/features/administration/schemas/administration";
import { useAuth } from "@/lib/auth/auth-context";

/** User administration ({@code specs/20-P11-Administration-V1.5.md §12, §65, §68}). */
export function UsersScreen() {
  const { can } = useAuth();
  const [statusDraft, setStatusDraft] = useState("");
  const [roleDraft, setRoleDraft] = useState("");
  const [searchDraft, setSearchDraft] = useState("");
  const [filters, setFilters] = useState({ status: "", roleId: "", search: "" });
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState | null>(null);
  const [target, setTarget] = useState<UserSummary | null>(null);

  const rolesQuery = useRoles(can("ROLE_VIEW"));
  const usersQuery = useUsers({
    status: filters.status || undefined,
    roleId: filters.roleId || undefined,
    search: filters.search || undefined,
    sort: toSortParam(sort, "users"),
    page,
  });
  const archive = useArchiveUser();
  const canArchive = can("USER_ARCHIVE");

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setPage(0);
    setFilters({ status: statusDraft, roleId: roleDraft, search: searchDraft });
  };

  const resetFilters = () => {
    setStatusDraft("");
    setRoleDraft("");
    setSearchDraft("");
    setPage(0);
    setFilters({ status: "", roleId: "", search: "" });
  };

  const columns: DataTableColumn<UserSummary>[] = [
    {
      key: "name",
      header: "Name",
      sortField: "firstName",
      render: (user) => `${user.firstName} ${user.lastName}`.trim(),
    },
    { key: "email", header: "Email", sortField: "email", render: (user) => user.email },
    {
      key: "roles",
      header: "Roles",
      render: (user) => (user.roles.length > 0 ? user.roles.join(", ") : "No role"),
    },
    {
      key: "status",
      header: "Status",
      sortField: "status",
      render: (user) => <StatusBadge status={user.status} />,
    },
    {
      key: "actions",
      header: "Actions",
      render: (user) => (
        <div className="flex items-center gap-3">
          <Link href={"/admin/users/" + user.id} className="font-medium text-blue-600 hover:underline">
            View
          </Link>
          {canArchive && user.status !== "ARCHIVED" ? (
            <button
              type="button"
              onClick={() => setTarget(user)}
              className="rounded text-sm font-medium text-red-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-700"
            >
              Archive
            </button>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-4">
      <FilterBar label="Filter users" onSubmit={applyFilters} onReset={resetFilters}>
        <div className="space-y-1">
          <label htmlFor="user-status" className="text-sm font-medium">Status</label>
          <select
            id="user-status"
            value={statusDraft}
            onChange={(event) => setStatusDraft(event.target.value)}
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          >
            <option value="">All</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </div>
        <div className="space-y-1">
          <label htmlFor="user-role" className="text-sm font-medium">Role</label>
          <select
            id="user-role"
            value={roleDraft}
            onChange={(event) => setRoleDraft(event.target.value)}
            disabled={rolesQuery.isPending}
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          >
            <option value="">All roles</option>
            {/*
              SUPER_ADMIN is a platform role: it never belongs to this organization and cannot be
              assigned from here, so offering it as a filter can only ever return an empty list.
              The backend catalogue is the source of truth; this is the same exclusion the create and
              edit user forms already apply.
            */}
            {(rolesQuery.data?.items ?? [])
              .filter((role) => role.code !== "SUPER_ADMIN")
              .map((role) => (
                <option key={role.id} value={role.id}>{role.name}</option>
              ))}
          </select>
        </div>
        <div className="space-y-1">
          <label htmlFor="user-search" className="text-sm font-medium">Search</label>
          <input
            id="user-search"
            value={searchDraft}
            onChange={(event) => setSearchDraft(event.target.value)}
            placeholder="Name or email"
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          />
        </div>
      </FilterBar>

      <DataTable
        caption="Users"
        columns={columns}
        rows={usersQuery.data?.items ?? []}
        getRowKey={(user) => user.id}
        loading={usersQuery.isPending}
        error={usersQuery.isError ? describeAdminError(usersQuery.error, "Users could not be loaded.") : null}
        onRetry={() => {
          void usersQuery.refetch();
        }}
        emptyMessage="No matching users."
        sort={sort}
        onSortChange={(field) => {
          setSort((current) => toggleSort(current, "users", field));
          setPage(0);
        }}
        footer={
          <PaginationBar
            page={usersQuery.data?.page ?? 0}
            totalPages={usersQuery.data?.totalPages ?? 1}
            onPageChange={setPage}
          />
        }
      />

      <DangerActionDialog
        open={target !== null}
        title="Archive user"
        description={
          target
            ? `Archive ${target.firstName} ${target.lastName}? The backend soft-deactivates the account; historical data stays queryable.`
            : ""
        }
        confirmLabel="Archive user"
        busy={archive.isPending}
        onConfirm={() => {
          if (!target) {
            return;
          }
          archive.mutate(target.id, {
            onSuccess: () => setTarget(null),
          });
        }}
        onCancel={() => setTarget(null)}
      />
      {archive.isError ? (
        <p role="alert" className="text-sm text-red-600">
          {describeAdminError(archive.error, "The user could not be archived.")}
        </p>
      ) : null}
      <p className="text-xs text-muted-foreground">Admin actions are audited by the backend.</p>
    </div>
  );
}
