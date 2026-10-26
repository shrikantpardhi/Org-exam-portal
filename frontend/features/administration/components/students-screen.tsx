"use client";

import { useState } from "react";

import { DataTable, PaginationBar, type DataTableColumn } from "@/components/admin/data-table";
import { StatusBadge } from "@/components/admin/status-badge";
import { describeAdminError } from "@/components/admin/format";
import { useRoles, useUsers } from "@/features/administration/hooks/use-administration";
import type { UserSummary } from "@/features/administration/schemas/administration";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * Student administration ({@code specs/20-P11-Administration-V1.5.md §13, §75}).
 *
 * <p>There is no frozen student-admin endpoint. The screen composes the role catalog (to resolve the
 * STUDENT role) with the user list filtered by that role — a UI composition, not a new aggregate.
 */
export function StudentsScreen() {
  const { can } = useAuth();
  const [page, setPage] = useState(0);
  const canViewRoles = can("ROLE_VIEW");
  const rolesQuery = useRoles(canViewRoles);
  const studentRole = rolesQuery.data?.items.find((role) => role.code === "STUDENT");
  const usersQuery = useUsers({ roleId: studentRole?.id, page }, Boolean(studentRole));

  if (!canViewRoles) {
    return (
      <p role="alert" className="text-sm text-red-600">
        Viewing students requires the role-visibility permission, because the STUDENT role must be
        resolved before the user list can be filtered.
      </p>
    );
  }

  if (rolesQuery.isPending) {
    return <p role="status" className="text-sm text-muted-foreground">Loading students…</p>;
  }

  if (rolesQuery.isError) {
    return (
      <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        {describeAdminError(rolesQuery.error, "The role catalog could not be loaded.")}
      </div>
    );
  }

  if (!studentRole) {
    return (
      <p role="note" className="rounded-lg border border-dashed border-input bg-muted p-4 text-sm text-foreground">
        No STUDENT role is configured, so there are no students to show.
      </p>
    );
  }

  const columns: DataTableColumn<UserSummary>[] = [
    { key: "name", header: "Name", render: (user) => `${user.firstName} ${user.lastName}`.trim() },
    { key: "email", header: "Email", render: (user) => user.email },
    { key: "mobile", header: "Mobile", render: (user) => user.mobile ?? "Not set" },
    { key: "status", header: "Status", render: (user) => <StatusBadge status={user.status} /> },
  ];

  return (
    <DataTable
      caption="Students"
      columns={columns}
      rows={usersQuery.data?.items ?? []}
      getRowKey={(user) => user.id}
      loading={usersQuery.isPending}
      error={usersQuery.isError ? describeAdminError(usersQuery.error, "Students could not be loaded.") : null}
      onRetry={() => {
        void usersQuery.refetch();
      }}
      emptyMessage="No matching students."
      footer={
        <PaginationBar
          page={usersQuery.data?.page ?? 0}
          totalPages={usersQuery.data?.totalPages ?? 1}
          onPageChange={setPage}
        />
      }
    />
  );
}
