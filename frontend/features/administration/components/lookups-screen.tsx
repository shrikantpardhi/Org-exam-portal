"use client";

import { useState, type FormEvent } from "react";

import { AdminForm } from "@/components/admin/admin-form";
import { DangerActionDialog } from "@/components/admin/danger-action-dialog";
import { DataTable, type DataTableColumn } from "@/components/admin/data-table";
import { FilterBar } from "@/components/admin/filter-bar";
import { StatusBadge } from "@/components/admin/status-badge";
import { describeAdminError } from "@/components/admin/format";
import {
  lookupCreateSchema,
  lookupUpdateSchema,
} from "@/features/administration/schemas/administration";
import type { Lookup } from "@/features/administration/schemas/administration";
import {
  useCreateLookup,
  useDeleteLookup,
  useLookups,
  useUpdateLookup,
} from "@/features/administration/hooks/use-administration";
import { useAuth } from "@/lib/auth/auth-context";

/** Lookup administration ({@code specs/20-P11-Administration-V1.5.md §10, §72}). */
export function LookupsScreen() {
  const { can } = useAuth();
  const [categoryDraft, setCategoryDraft] = useState("");
  const [statusDraft, setStatusDraft] = useState("");
  const [applied, setApplied] = useState({ category: "", status: "" });
  const [editing, setEditing] = useState<Lookup | null>(null);
  const [target, setTarget] = useState<Lookup | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const query = useLookups({
    category: applied.category || undefined,
    status: applied.status || undefined,
  });
  const create = useCreateLookup();
  const update = useUpdateLookup();
  const remove = useDeleteLookup();

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setApplied({ category: categoryDraft, status: statusDraft });
  };

  const resetFilters = () => {
    setCategoryDraft("");
    setStatusDraft("");
    setApplied({ category: "", status: "" });
  };

  const columns: DataTableColumn<Lookup>[] = [
    { key: "category", header: "Category", render: (lookup) => lookup.category },
    { key: "code", header: "Code", render: (lookup) => <code className="text-xs">{lookup.code}</code> },
    { key: "name", header: "Name", render: (lookup) => lookup.name },
    { key: "order", header: "Order", render: (lookup) => lookup.displayOrder ?? "—" },
    { key: "status", header: "Status", render: (lookup) => <StatusBadge status={lookup.status} /> },
    {
      key: "actions",
      header: "Actions",
      render: (lookup) => (
        <div className="flex flex-wrap gap-3">
          {can("LOOKUP_UPDATE") ? (
            <button
              type="button"
              onClick={() => {
                setEditing(lookup);
                setMessage(null);
              }}
              className="rounded text-sm font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
            >
              Edit
            </button>
          ) : null}
          {can("LOOKUP_DELETE") && lookup.status !== "ARCHIVED" ? (
            <button
              type="button"
              onClick={() => setTarget(lookup)}
              className="rounded text-sm font-medium text-red-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-700"
            >
              Delete
            </button>
          ) : null}
          {!can("LOOKUP_UPDATE") && !can("LOOKUP_DELETE") ? (
            <span className="text-xs text-slate-500">View only</span>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <FilterBar label="Filter lookups" onSubmit={applyFilters} onReset={resetFilters}>
        <div className="space-y-1">
          <label htmlFor="lookup-category-filter" className="text-sm font-medium">Category</label>
          <input
            id="lookup-category-filter"
            value={categoryDraft}
            onChange={(event) => setCategoryDraft(event.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div className="space-y-1">
          <label htmlFor="lookup-status-filter" className="text-sm font-medium">Status</label>
          <select
            id="lookup-status-filter"
            value={statusDraft}
            onChange={(event) => setStatusDraft(event.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">All</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </div>
      </FilterBar>

      <DataTable
        caption="Lookup values"
        columns={columns}
        rows={query.data ?? []}
        getRowKey={(lookup) => lookup.id}
        loading={query.isPending}
        error={query.isError ? describeAdminError(query.error, "Lookups could not be loaded.") : null}
        onRetry={() => {
          void query.refetch();
        }}
        emptyMessage="No lookup values found."
      />

      {can("LOOKUP_CREATE") ? (
        <section aria-labelledby="create-lookup-heading" className="space-y-3">
          <h2 id="create-lookup-heading" className="text-base font-semibold">Create a lookup value</h2>
          <AdminForm
            schema={lookupCreateSchema}
            submitLabel="Create lookup"
            resetOnSuccess
            fields={[
              { name: "category", label: "Category" },
              { name: "code", label: "Code" },
              { name: "name", label: "Name" },
              { name: "displayOrder", label: "Display order", type: "number" },
            ]}
            onSubmit={async (values) => {
              await create.mutateAsync({
                category: values.category,
                code: values.code,
                name: values.name,
                ...(values.displayOrder === undefined ? {} : { displayOrder: values.displayOrder }),
              });
              setMessage("Lookup created.");
            }}
          />
        </section>
      ) : null}

      {editing ? (
        <section aria-labelledby="edit-lookup-heading" className="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
          <h2 id="edit-lookup-heading" className="text-base font-semibold">
            Edit {editing.code}
          </h2>
          <p className="text-sm text-slate-500">
            Category and code are stable keys; the frozen update accepts name and display order.
            Activation is not exposed by this API.
          </p>
          <AdminForm
            schema={lookupUpdateSchema}
            submitLabel="Save lookup"
            defaultValues={{
              name: editing.name,
              displayOrder: editing.displayOrder === null ? "" : String(editing.displayOrder),
            }}
            fields={[
              { name: "name", label: "Name" },
              { name: "displayOrder", label: "Display order", type: "number" },
            ]}
            onSubmit={async (values) => {
              await update.mutateAsync({
                lookupId: editing.id,
                body: {
                  name: values.name,
                  ...(values.displayOrder === undefined ? {} : { displayOrder: values.displayOrder }),
                },
              });
              setMessage("Lookup updated.");
              setEditing(null);
            }}
          />
          <button
            type="button"
            onClick={() => setEditing(null)}
            className="rounded border border-slate-300 bg-white px-4 py-2 text-sm font-medium focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            Cancel
          </button>
        </section>
      ) : null}

      {message ? <p role="status" className="text-sm text-emerald-700">{message}</p> : null}
      {create.isError ? (
        <p role="alert" className="text-sm text-red-600">
          {describeAdminError(create.error, "The lookup could not be created.")}
        </p>
      ) : null}
      {update.isError ? (
        <p role="alert" className="text-sm text-red-600">
          {describeAdminError(update.error, "The lookup could not be saved.")}
        </p>
      ) : null}
      {remove.isError ? (
        <p role="alert" className="text-sm text-red-600">
          {describeAdminError(remove.error, "The lookup could not be deleted.")}
        </p>
      ) : null}

      <DangerActionDialog
        open={target !== null}
        title="Delete lookup value"
        description={
          target
            ? `Delete ${target.category} / ${target.code}? If the value is an application contract, the backend archives it instead of removing it.`
            : ""
        }
        confirmLabel="Delete lookup"
        busy={remove.isPending}
        onConfirm={() => {
          if (!target) {
            return;
          }
          remove.mutate(target.id, {
            onSuccess: () => setTarget(null),
          });
        }}
        onCancel={() => setTarget(null)}
      />
    </div>
  );
}
