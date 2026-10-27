"use client";

import Link from "next/link";
import { useState } from "react";

import { ConfirmDialog } from "@/components/admin/confirm-dialog";
import { describeAdminError } from "@/components/admin/format";
import { StatusBadge } from "@/components/admin/status-badge";
import { useBreadcrumbLabel } from "@/components/shell/breadcrumbs";
import { Alert } from "@/components/ui/alert";
import { DataGrid, type DataGridColumn, type PageSize } from "@/components/ui/data-grid";
import { FormDialog } from "@/components/ui/form-dialog";
import { useRoles } from "@/features/administration/hooks/use-administration";
import {
  useArchiveOrganizationAdmin,
  useOrganization,
  useOrganizationAdmins,
  useRestoreOrganizationAdmin,
  useSetOrganizationPrimaryAdmin,
  useUpdateOrganizationAdminRoles,
} from "@/features/platform/hooks/use-platform";
import type { AdminListEntry } from "@/features/platform/schemas/platform";

/** The status filter the operator can choose. "All" means the server's default: not archived. */
type StatusFilter = "ALL" | "ACTIVE" | "ARCHIVED";

const STATUS_FILTERS: { value: StatusFilter; label: string; hint: string }[] = [
  { value: "ALL", label: "All", hint: "Currently-serving administrators, excluding archived ones." },
  { value: "ACTIVE", label: "Active", hint: "Only administrators that are currently serving." },
  { value: "ARCHIVED", label: "Archived", hint: "Only archived administrators." },
];

/**
 * Roles the server will never accept from this screen.
 *
 * <p>ADMIN is what makes the account an administrator at all, and SUPER_ADMIN is a platform role;
 * the server refuses both on the roles endpoint (409), so offering them would only produce an
 * error the operator cannot act on.
 */
const EXCLUDED_ROLE_CODES = new Set(["ADMIN", "SUPER_ADMIN"]);

/**
 * An organization's administrators, with the CRUD the platform owner needs (ADR-0053, ADR-0054).
 *
 * <p>Before this screen the only administrator the platform owner could see was the count on the
 * organization detail, and the only action was "create". A mistyped invitation was therefore
 * unfixable from the UI. This screen closes that: it lists the administrators and lets the owner
 * correct roles, transfer the primary designation, and archive or restore an account.
 *
 * <p>Archiving is reversible and keeps role history, so the action is named "Archive" rather than
 * "Deactivate" and archived rows get a "Restore" action. Two refusals are mirrored rather than
 * hidden: the primary administrator cannot be archived (the organization would lose the only
 * account able to edit roles, ADR-0052) and archived accounts cannot be edited until restored.
 * The UI states the reason so the disabled control is explicable.
 *
 * <p>The roles editor deliberately lives outside the table, in a {@link FormDialog}. Rendering
 * inputs inside a TanStack cell makes React recreate the node on every keystroke, which drops focus
 * and swallows all but the first character typed; a dialog keeps the fields stable and, unlike the
 * inline panel it replaces, keeps the table where it was instead of pushing it down the page.
 */
export function OrganizationAdminsScreen({ organizationId }: { organizationId: string }) {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState<PageSize>(10);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [editing, setEditing] = useState<string | null>(null);
  const [rolesDraft, setRolesDraft] = useState<string[]>([]);
  const [confirmingArchive, setConfirmingArchive] = useState<AdminListEntry | null>(null);

  const organization = useOrganization(organizationId);
  const query = useOrganizationAdmins(organizationId, {
    // "All" sends no status at all: the server's default is "not archived", which is deliberately
    // broader than status=ACTIVE (it would still include INACTIVE accounts).
    status: statusFilter === "ALL" ? undefined : statusFilter,
    search: search || undefined,
    page,
    size: pageSize,
  });
  // Fetched only once the editor is open: the list is small but it is not needed to render the grid.
  const rolesQuery = useRoles(editing !== null);
  const updateRoles = useUpdateOrganizationAdminRoles(organizationId);
  const archive = useArchiveOrganizationAdmin(organizationId);
  const restore = useRestoreOrganizationAdmin(organizationId);
  const setPrimary = useSetOrganizationPrimaryAdmin(organizationId);

  // Without this the trail shows the organization uuid: the route carries only the id.
  useBreadcrumbLabel(organizationId, organization.data?.name);

  const primaryUserId = organization.data?.primaryAdminUserId ?? null;
  const editingAdmin =
    query.data?.items.find((admin) => admin.userId === editing) ?? null;

  const assignableRoles = (rolesQuery.data?.items ?? []).filter(
    (role) => role.status === "ACTIVE" && !EXCLUDED_ROLE_CODES.has(role.code.toUpperCase()),
  );

  /** Opens the editor with the row's current roles already ticked. */
  const startEditing = (admin: AdminListEntry) => {
    setRolesDraft(
      admin.roles
        .map((code) => code.toUpperCase())
        .filter((code) => !EXCLUDED_ROLE_CODES.has(code)),
    );
    setEditing(admin.userId);
  };

  const toggleRole = (code: string) => {
    setRolesDraft((previous) =>
      previous.includes(code) ? previous.filter((value) => value !== code) : [...previous, code],
    );
  };

  const changeStatusFilter = (next: StatusFilter) => {
    setStatusFilter(next);
    // A different filter is a different result set; keeping the page index would show an empty page.
    setPage(0);
    setEditing(null);
  };

  /**
   * Saves the ticked roles.
   *
   * <p>The payload is deliberately narrowed to the assignable list: the row may still carry a code
   * that is not an active assignment choice, and the server rejects unknown codes with 409, which
   * would fail the whole save for a field the operator never touched.
   */
  const saveRoles = () => {
    // A save started before the assignable list arrived would send an empty set and wipe the
    // administrator's roles, so the click is ignored until the list is known to be good.
    if (!editingAdmin || rolesQuery.isPending || rolesQuery.isError) {
      return;
    }
    const assignable = new Set(assignableRoles.map((role) => role.code));
    const roles = rolesDraft.filter((code) => assignable.has(code));
    updateRoles.mutate(
      { userId: editingAdmin.userId, roles },
      { onSuccess: () => setEditing(null) },
    );
  };

  const columns: DataGridColumn<AdminListEntry>[] = [
    {
      key: "name",
      header: "Name",
      sortField: "firstName",
      render: (admin) => (
        <span className="font-medium text-foreground">
          {admin.firstName} {admin.lastName}
        </span>
      ),
    },
    { key: "email", header: "Email", sortField: "email", render: (admin) => admin.email },
    {
      key: "status",
      header: "Status",
      sortField: "status",
      render: (admin) => (
        <span className="inline-flex flex-col gap-1">
          <StatusBadge status={admin.status} />
          {admin.status === "ARCHIVED" ? (
            <span className="text-xs text-muted-foreground">
              Archived accounts keep their role history and can be restored.
            </span>
          ) : null}
        </span>
      ),
    },
    {
      key: "primary",
      header: "Primary",
      render: (admin) =>
        admin.isPrimary ? (
          <span className="rounded bg-primary-subtle px-2 py-0.5 text-xs font-medium text-primary">
            Primary
          </span>
        ) : (
          <span className="text-muted-foreground">—</span>
        ),
    },
    {
      key: "roles",
      header: "Other roles",
      render: (admin) =>
        admin.roles.length === 0 ? (
          <span className="text-muted-foreground">None</span>
        ) : (
          <span className="inline-flex flex-wrap gap-1">
            {admin.roles.map((role) => (
              <span key={role} className="rounded border border-border px-2 py-0.5 text-xs">
                {role}
              </span>
            ))}
          </span>
        ),
    },
    {
      key: "actions",
      header: "",
      render: (admin) => {
        const isPrimary = admin.isPrimary || admin.userId === primaryUserId;
        const isArchived = admin.status === "ARCHIVED";
        return (
          <div className="flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={() => startEditing(admin)}
              disabled={isArchived}
              title={
                isArchived
                  ? "An archived administrator's roles cannot be edited. Restore the account first."
                  : undefined
              }
              className="font-medium text-info hover:underline disabled:opacity-50"
            >
              Edit roles
            </button>

            {isPrimary ? null : (
              <button
                type="button"
                onClick={() => setPrimary.mutate(admin.userId)}
                disabled={isArchived || setPrimary.isPending}
                title={
                  isArchived
                    ? "An archived administrator cannot be made the primary administrator."
                    : "Transfer the primary designation to this administrator."
                }
                className="font-medium text-info hover:underline disabled:opacity-50"
              >
                Make primary
              </button>
            )}

            {isArchived ? (
              <button
                type="button"
                onClick={() => restore.mutate(admin.userId)}
                disabled={restore.isPending}
                className="font-medium text-primary hover:underline disabled:opacity-50"
              >
                Restore
              </button>
            ) : isPrimary ? (
              <span
                className="text-xs text-muted-foreground"
                title="The primary administrator cannot be archived; the organization would lose the only account able to edit roles (ADR-0052). Transfer the primary designation first."
              >
                Cannot archive the primary administrator
              </span>
            ) : (
              <button
                type="button"
                onClick={() => setConfirmingArchive(admin)}
                disabled={archive.isPending}
                className="font-medium text-destructive hover:underline disabled:opacity-50"
              >
                Archive
              </button>
            )}
          </div>
        );
      },
    },
  ];

  const mutationError =
    (updateRoles.error ? describeAdminError(updateRoles.error) : null) ??
    (archive.error ? describeAdminError(archive.error) : null) ??
    (restore.error ? describeAdminError(restore.error) : null) ??
    (setPrimary.error ? describeAdminError(setPrimary.error) : null);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <Link
            href={"/platform/organizations/" + organizationId}
            className="text-sm font-medium text-info hover:underline"
          >
            ← Back to organization
          </Link>
          <p className="mt-1 text-sm text-muted-foreground">
            {organization.data?.primaryAdminEmail
              ? "Primary administrator: " + organization.data.primaryAdminEmail
              : "No primary administrator is designated. Role editing is blocked until one is."}
          </p>
        </div>
        <Link
          href={"/platform/organizations/" + organizationId + "/admins/new"}
          className="rounded bg-primary px-3 py-2 text-sm font-medium text-primary-foreground"
        >
          Invite administrator
        </Link>
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <div className="flex-1">
          <label htmlFor="admin-search" className="sr-only">
            Search administrators
          </label>
          <input
            id="admin-search"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder="Search by name or email"
            className="w-full max-w-sm rounded-md border border-input bg-surface px-3 py-2 text-sm"
          />
        </div>

        <div className="flex items-center gap-2">
          <span id="admin-status-filter-label" className="text-sm font-medium text-foreground">
            Status
          </span>
          <div
            role="group"
            aria-labelledby="admin-status-filter-label"
            className="inline-flex overflow-hidden rounded-md border border-input"
          >
            {STATUS_FILTERS.map((option) => {
              const selected = statusFilter === option.value;
              return (
                <button
                  key={option.value}
                  type="button"
                  aria-pressed={selected}
                  title={option.hint}
                  onClick={() => changeStatusFilter(option.value)}
                  className={
                    "px-3 py-2 text-sm font-medium " +
                    (selected
                      ? "bg-primary text-primary-foreground"
                      : "bg-surface text-foreground hover:bg-muted")
                  }
                >
                  {option.label}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {mutationError ? <Alert tone="danger">{mutationError}</Alert> : null}

      <DataGrid
        caption={"Administrators of this organization"}
        columns={columns}
        rows={query.data?.items ?? []}
        getRowKey={(admin) => admin.userId}
        loading={query.isPending}
        error={query.error ? describeAdminError(query.error) : null}
        onRetry={() => void query.refetch()}
        emptyMessage={
          statusFilter === "ARCHIVED"
            ? "No archived administrators."
            : "No administrators match this search."
        }
        page={query.data?.page ?? 0}
        pageCount={query.data?.totalPages ?? 1}
        pageSize={pageSize}
        onPageSizeChange={(size) => {
          setPageSize(size);
          setPage(0);
        }}
        onPageChange={setPage}
        totalElements={query.data?.totalElements ?? 0}
      />

      {/*
        The roles editor is a dialog, not a panel and never a cell. Outside the table for the
        original reason — inputs inside a TanStack cell are recreated per keystroke — and in a
        dialog so opening it does not move the grid the operator was reading.
      */}
      <FormDialog
        open={editingAdmin !== null}
        title={editingAdmin ? "Roles for " + editingAdmin.email : "Roles"}
        description="Select any number of roles, including none. Administrator roles are managed separately."
        submitLabel="Save roles"
        busy={updateRoles.isPending}
        onCancel={() => {
          if (!updateRoles.isPending) {
            setEditing(null);
          }
        }}
        onSubmit={saveRoles}
      >
        {rolesQuery.isPending ? (
          <p role="status" className="text-sm text-muted-foreground">
            Loading roles…
          </p>
        ) : rolesQuery.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {describeAdminError(rolesQuery.error, "The role list could not be loaded.")}
          </p>
        ) : (
          <fieldset>
            <legend className="text-sm font-medium text-foreground">Roles</legend>
            <div className="mt-2 grid gap-2 sm:grid-cols-2">
              {assignableRoles.map((role) => (
                <label key={role.id} className="flex items-center gap-2 text-sm text-foreground">
                  <input
                    type="checkbox"
                    checked={rolesDraft.includes(role.code)}
                    onChange={() => toggleRole(role.code)}
                    className="h-4 w-4 rounded border-input"
                  />
                  <span>{role.name}</span>
                </label>
              ))}
            </div>
            {assignableRoles.length === 0 ? (
              <p className="mt-2 text-sm text-muted-foreground">
                No assignable roles are available.
              </p>
            ) : null}
          </fieldset>
        )}
      </FormDialog>

      <ConfirmDialog
        open={confirmingArchive !== null}
        title="Archive administrator?"
        description={
          confirmingArchive
            ? "Archive " +
              confirmingArchive.email +
              "? They lose access immediately but keep their role history and can be restored."
            : ""
        }
        confirmLabel="Archive"
        destructive
        busy={archive.isPending}
        onConfirm={() => {
          if (!confirmingArchive) {
            return;
          }
          archive.mutate(confirmingArchive.userId, {
            onSuccess: () => setConfirmingArchive(null),
            onError: () => setConfirmingArchive(null),
          });
        }}
        onCancel={() => setConfirmingArchive(null)}
      />
    </div>
  );
}
