"use client";

import { useEffect, useState } from "react";

import { DangerActionDialog } from "@/components/admin/danger-action-dialog";
import { DataTable, type DataTableColumn } from "@/components/admin/data-table";
import { StatusBadge } from "@/components/admin/status-badge";
import { describeAdminError } from "@/components/admin/format";
import { toSortParam, toggleSort, type SortState } from "@/components/admin/sorting";
import {
  usePermissions,
  useReplaceRolePermissions,
  useRolePermissions,
  useRoles,
} from "@/features/administration/hooks/use-administration";
import type { RoleSummary } from "@/features/administration/schemas/administration";
import { useAuth } from "@/lib/auth/auth-context";

/** Role administration and permission assignment ({@code specs/20-P11-Administration-V1.5.md §15, §16, §68}). */
export function RolesScreen() {
  const { can, user } = useAuth();
  const [sort, setSort] = useState<SortState | null>(null);
  const rolesQuery = useRoles(true, toSortParam(sort, "roles"));
  const permissionsQuery = usePermissions(0);
  const [selectedRoleId, setSelectedRoleId] = useState("");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const canViewPermissions = can("ROLE_PERMISSION_VIEW");
  const canAssign = can("ROLE_PERMISSION_ASSIGN");
  const rolePermissionsQuery = useRolePermissions(selectedRoleId, canViewPermissions);
  const replace = useReplaceRolePermissions(selectedRoleId);

  useEffect(() => {
    if (rolePermissionsQuery.data) {
      setSelectedIds(rolePermissionsQuery.data.map((permission) => permission.id));
    }
  }, [rolePermissionsQuery.data]);

  const selectedRole = rolesQuery.data?.items.find((role) => role.id === selectedRoleId);

  /**
   * True when the selected role is one the signed-in administrator actually holds.
   *
   * <p>Editing your own role's permissions is a privilege-escalation path: an administrator could
   * grant themselves anything, and then the assignment is no longer reviewable by anyone with less
   * access. The backend enforces the ceiling (ADR-0044), but the screen should not invite the action
   * in the first place. The panel therefore renders read-only for the caller's own roles.
   */
  const editingOwnRole =
    selectedRole !== undefined && (user?.roles ?? []).some((code) => code === selectedRole.code);

  const closePanel = () => {
    setSelectedRoleId("");
    setSelectedIds([]);
    setMessage(null);
    setConfirmOpen(false);
  };

  const toggle = (permissionId: string) => {
    setSelectedIds((current) =>
      current.includes(permissionId)
        ? current.filter((id) => id !== permissionId)
        : [...current, permissionId],
    );
  };

  const columns: DataTableColumn<RoleSummary>[] = [
    {
      key: "code",
      header: "Code",
      sortField: "code",
      render: (role) => <code className="text-xs">{role.code}</code>,
    },
    { key: "name", header: "Name", sortField: "name", render: (role) => role.name },
    { key: "description", header: "Description", render: (role) => role.description ?? "No description" },
    {
      key: "status",
      header: "Status",
      sortField: "status",
      render: (role) => <StatusBadge status={role.status} />,
    },
    {
      key: "actions",
      header: "Actions",
      render: (role) =>
        canViewPermissions ? (
          <button
            type="button"
            onClick={() => {
              setSelectedRoleId(role.id);
              setMessage(null);
            }}
            className="rounded text-sm font-medium text-info underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            Manage permissions
          </button>
        ) : (
          <span className="text-xs text-muted-foreground">View only</span>
        ),
    },
  ];

  return (
    <div className="space-y-6">
      <DataTable
        caption="Roles"
        columns={columns}
        rows={(rolesQuery.data?.items ?? []).filter((role) => role.code !== "SUPER_ADMIN")}
        getRowKey={(role) => role.id}
        loading={rolesQuery.isPending}
        error={rolesQuery.isError ? describeAdminError(rolesQuery.error, "Roles could not be loaded.") : null}
        onRetry={() => {
          void rolesQuery.refetch();
        }}
        emptyMessage="No roles found."
        sort={sort}
        onSortChange={(field) => setSort((current) => toggleSort(current, "roles", field))}
      />

      {selectedRoleId.length > 0 ? (
        <section aria-labelledby="role-permissions-heading" className="space-y-3 rounded-lg border border-border bg-surface p-4">
          <div>
            <h2 id="role-permissions-heading" className="text-base font-semibold">
              Permissions for {selectedRole?.name ?? "role"}
            </h2>
            <p className="text-sm text-muted-foreground">
              A full replacement is saved atomically. The confirmation is UX safety; the backend
              enforces the assignment permission.
            </p>
          </div>

          {rolePermissionsQuery.isPending ? (
            <p role="status" className="text-sm text-muted-foreground">Loading role permissions…</p>
          ) : null}
          {rolePermissionsQuery.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {describeAdminError(rolePermissionsQuery.error, "Role permissions could not be loaded.")}
            </p>
          ) : null}
          {permissionsQuery.isPending ? (
            <p role="status" className="text-sm text-muted-foreground">Loading permission catalogue…</p>
          ) : null}
          {permissionsQuery.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {describeAdminError(permissionsQuery.error, "The permission catalogue could not be loaded.")}
            </p>
          ) : null}

          {permissionsQuery.data ? (
            <ul className="grid max-h-80 gap-1 overflow-y-auto text-sm sm:grid-cols-2">
              {permissionsQuery.data.items.map((permission) => (
                <li key={permission.id}>
                  <label className="flex items-start gap-2">
                    <input
                      type="checkbox"
                      checked={selectedIds.includes(permission.id)}
                      onChange={() => toggle(permission.id)}
                      disabled={!canAssign || editingOwnRole}
                      className="mt-1"
                    />
                    <span>
                      <span className="font-mono text-xs">{permission.code}</span>
                      <span className="block text-xs text-muted-foreground">{permission.name}</span>
                    </span>
                  </label>
                </li>
              ))}
            </ul>
          ) : null}

          {editingOwnRole ? (
            <p className="text-xs text-muted-foreground">
              This is one of your own roles, so its permissions are read-only here. Ask another
              administrator to change it — that keeps every grant reviewable by a second person.
            </p>
          ) : canAssign ? (
            <button
              type="button"
              onClick={() => setConfirmOpen(true)}
              disabled={!permissionsQuery.data}
              className="rounded bg-blue-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700"
            >
              Save permissions
            </button>
          ) : (
            <p className="text-xs text-muted-foreground">
              You can view assignments but not change them.
            </p>
          )}

          <div>
            <button
              type="button"
              onClick={closePanel}
              className="rounded-md border border-input bg-surface px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-muted"
            >
              Cancel
            </button>
          </div>

          {message ? (
            <p role="status" className="text-sm text-success">{message}</p>
          ) : null}
          {replace.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {describeAdminError(replace.error, "The role permissions could not be saved.")}
            </p>
          ) : null}
        </section>
      ) : null}

      <DangerActionDialog
        open={confirmOpen}
        title="Replace role permissions"
        description={
          selectedRole
            ? `Replace every permission assignment on ${selectedRole.name}. This changes what every user with this role may do.`
            : "Replace this role's permission assignments."
        }
        confirmLabel="Save permissions"
        busy={replace.isPending}
        onConfirm={() => {
          replace.mutate(selectedIds, {
            onSuccess: () => {
              setConfirmOpen(false);
              setMessage("Role permissions updated.");
            },
          });
        }}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  );
}
