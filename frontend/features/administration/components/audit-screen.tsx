"use client";

import { useState, type FormEvent } from "react";

import { AuditDrawer } from "@/components/admin/audit-drawer";
import { DataTable, PaginationBar, type DataTableColumn } from "@/components/admin/data-table";
import { FilterBar } from "@/components/admin/filter-bar";
import { describeAdminError, formatDateTime, shortId } from "@/components/admin/format";
import { toSortParam, toggleSort, type SortState } from "@/components/admin/sorting";
import type { AuditFilters } from "@/features/administration/api/administration-api";
import { auditFilterSchema, toIsoInstant } from "@/features/administration/schemas/administration";
import type { AuditLog } from "@/features/administration/schemas/administration";
import { useAuditLogs } from "@/features/administration/hooks/use-administration";

/**
 * The audit explorer ({@code specs/20-P11-Administration-V1.5.md §11.8, §11.9, §73}).
 *
 * <p>Filter by actor, entity, entity id, action and date range. The screen is read-only: the audit
 * trail has no mutation endpoint and none is offered.
 */
export function AuditScreen() {
  const [draft, setDraft] = useState({
    userId: "",
    entityName: "",
    entityId: "",
    action: "",
    from: "",
    to: "",
  });
  const [applied, setApplied] = useState<AuditFilters>({});
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [selected, setSelected] = useState<AuditLog | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const query = useAuditLogs({ ...applied, page, sort: toSortParam(sort, "audit") });

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const parsed = auditFilterSchema.safeParse(draft);
    if (!parsed.success) {
      setFormError(parsed.error.issues[0]?.message ?? "Check the filter values.");
      return;
    }
    setFormError(null);
    setPage(0);
    setApplied({
      userId: parsed.data.userId || undefined,
      entityName: parsed.data.entityName || undefined,
      entityId: parsed.data.entityId || undefined,
      action: parsed.data.action || undefined,
      from: toIsoInstant(parsed.data.from),
      to: toIsoInstant(parsed.data.to),
    });
  };

  const resetFilters = () => {
    setDraft({ userId: "", entityName: "", entityId: "", action: "", from: "", to: "" });
    setApplied({});
    setPage(0);
    setFormError(null);
  };

  const columns: DataTableColumn<AuditLog>[] = [
    {
      key: "when",
      header: "Timestamp",
      sortField: "createdOn",
      render: (entry) => formatDateTime(entry.createdOn) ?? "Not available",
    },
    {
      key: "actor",
      header: "Actor",
      render: (entry) => (entry.userId ? shortId(entry.userId) : "System"),
    },
    {
      key: "entity",
      header: "Entity",
      sortField: "entityName",
      render: (entry) =>
        entry.entityName
          ? `${entry.entityName}${entry.entityId ? ` · ${shortId(entry.entityId)}` : ""}`
          : "Not available",
    },
    {
      key: "action",
      header: "Action",
      sortField: "action",
      render: (entry) => entry.action ?? "Not available",
    },
    { key: "remarks", header: "Remarks", render: (entry) => entry.remarks ?? "No remarks" },
    {
      key: "details",
      header: "Details",
      render: (entry) => (
        <button
          type="button"
          onClick={() => {
            setSelected(entry);
            setDrawerOpen(true);
          }}
          className="rounded text-sm font-medium text-info underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          View
        </button>
      ),
    },
  ];

  return (
    <div className="space-y-4">
      <FilterBar label="Filter audit events" onSubmit={applyFilters} onReset={resetFilters} busy={query.isFetching}>
        <div className="space-y-1">
          <label htmlFor="audit-actor" className="text-sm font-medium">Actor user id</label>
          <input
            id="audit-actor"
            value={draft.userId}
            onChange={(event) => setDraft({ ...draft, userId: event.target.value })}
            placeholder="UUID"
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          />
        </div>
        <div className="space-y-1">
          <label htmlFor="audit-entity" className="text-sm font-medium">Entity</label>
          <input
            id="audit-entity"
            value={draft.entityName}
            onChange={(event) => setDraft({ ...draft, entityName: event.target.value })}
            placeholder="For example User"
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          />
        </div>
        <div className="space-y-1">
          <label htmlFor="audit-entity-id" className="text-sm font-medium">Entity id</label>
          <input
            id="audit-entity-id"
            value={draft.entityId}
            onChange={(event) => setDraft({ ...draft, entityId: event.target.value })}
            placeholder="UUID"
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          />
        </div>
        <div className="space-y-1">
          <label htmlFor="audit-action" className="text-sm font-medium">Action</label>
          <input
            id="audit-action"
            value={draft.action}
            onChange={(event) => setDraft({ ...draft, action: event.target.value })}
            placeholder="For example CREATE"
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          />
        </div>
        <div className="space-y-1">
          <label htmlFor="audit-from" className="text-sm font-medium">From</label>
          <input
            id="audit-from"
            type="datetime-local"
            value={draft.from}
            onChange={(event) => setDraft({ ...draft, from: event.target.value })}
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          />
        </div>
        <div className="space-y-1">
          <label htmlFor="audit-to" className="text-sm font-medium">To</label>
          <input
            id="audit-to"
            type="datetime-local"
            value={draft.to}
            onChange={(event) => setDraft({ ...draft, to: event.target.value })}
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          />
        </div>
      </FilterBar>

      {formError ? (
        <p role="alert" className="text-sm text-destructive">{formError}</p>
      ) : null}

      <DataTable
        caption="Audit events"
        columns={columns}
        rows={query.data?.items ?? []}
        getRowKey={(entry) => entry.id}
        loading={query.isPending}
        error={query.isError ? describeAdminError(query.error, "Audit events could not be loaded.") : null}
        onRetry={() => {
          void query.refetch();
        }}
        emptyMessage="No audit events match the filter."
        sort={sort}
        onSortChange={(field) => {
          setSort((current) => toggleSort(current, "audit", field));
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

      <AuditDrawer entry={selected} open={drawerOpen} onClose={() => setDrawerOpen(false)} />
    </div>
  );
}
