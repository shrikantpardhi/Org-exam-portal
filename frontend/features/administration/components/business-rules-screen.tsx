"use client";

import { useState } from "react";

import { AdminForm } from "@/components/admin/admin-form";
import { DangerActionDialog } from "@/components/admin/danger-action-dialog";
import { DataTable, type DataTableColumn } from "@/components/admin/data-table";
import { StatusBadge } from "@/components/admin/status-badge";
import { describeAdminError } from "@/components/admin/format";
import { businessRuleCreateSchema, parseJsonObject } from "@/features/administration/schemas/administration";
import type { BusinessRule } from "@/features/administration/schemas/administration";
import {
  useBusinessRules,
  useCreateBusinessRule,
  useUpdateBusinessRule,
} from "@/features/administration/hooks/use-administration";
import { useAuth } from "@/lib/auth/auth-context";

/** Business-rule configuration ({@code specs/20-P11-Administration-V1.5.md §9, §71}). */
export function BusinessRulesScreen() {
  const { can } = useAuth();
  const query = useBusinessRules();
  const create = useCreateBusinessRule();
  const update = useUpdateBusinessRule();
  const [editing, setEditing] = useState<BusinessRule | null>(null);
  const [configText, setConfigText] = useState("");
  const [configError, setConfigError] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const columns: DataTableColumn<BusinessRule>[] = [
    { key: "code", header: "Code", render: (rule) => <code className="text-xs">{rule.code}</code> },
    { key: "status", header: "Status", render: (rule) => <StatusBadge status={rule.status} /> },
    {
      key: "configuration",
      header: "Configuration",
      render: (rule) => (
        <code className="block max-w-md truncate text-xs" title={JSON.stringify(rule.configuration)}>
          {JSON.stringify(rule.configuration)}
        </code>
      ),
    },
    {
      key: "actions",
      header: "Actions",
      render: (rule) =>
        can("BUSINESS_RULE_UPDATE") ? (
          <button
            type="button"
            onClick={() => {
              setEditing(rule);
              setConfigText(JSON.stringify(rule.configuration, null, 2));
              setConfigError(null);
              setMessage(null);
            }}
            className="rounded text-sm font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            Edit
          </button>
        ) : (
          <span className="text-xs text-slate-500">View only</span>
        ),
    },
  ];

  return (
    <div className="space-y-6">
      <DataTable
        caption="Business rules"
        columns={columns}
        rows={query.data ?? []}
        getRowKey={(rule) => rule.id}
        loading={query.isPending}
        error={query.isError ? describeAdminError(query.error, "Business rules could not be loaded.") : null}
        onRetry={() => {
          void query.refetch();
        }}
        emptyMessage="No business rules found."
      />

      {can("BUSINESS_RULE_CREATE") ? (
        <section aria-labelledby="create-rule-heading" className="space-y-3">
          <h2 id="create-rule-heading" className="text-base font-semibold">Create a business rule</h2>
          <AdminForm
            schema={businessRuleCreateSchema}
            submitLabel="Create rule"
            resetOnSuccess
            fields={[
              { name: "code", label: "Code" },
              { name: "configuration", label: "Configuration (JSON object)", type: "textarea" },
            ]}
            onSubmit={async (values) => {
              await create.mutateAsync({
                code: values.code,
                configuration: parseJsonObject(values.configuration),
              });
              setMessage("Business rule created.");
            }}
          />
        </section>
      ) : null}

      {editing ? (
        <section aria-labelledby="edit-rule-heading" className="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
          <h2 id="edit-rule-heading" className="text-base font-semibold">
            Edit {editing.code}
          </h2>
          <label htmlFor="rule-configuration" className="text-sm font-medium">
            Configuration (JSON object)
          </label>
          <textarea
            id="rule-configuration"
            rows={8}
            value={configText}
            onChange={(event) => setConfigText(event.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm"
          />
          {configError ? <p role="alert" className="text-sm text-red-600">{configError}</p> : null}
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => {
                try {
                  parseJsonObject(configText);
                  setConfigError(null);
                  setConfirmOpen(true);
                } catch {
                  setConfigError("Enter a JSON object.");
                }
              }}
              className="rounded bg-blue-700 px-4 py-2 text-sm font-medium text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700"
            >
              Save rule
            </button>
            <button
              type="button"
              onClick={() => setEditing(null)}
              className="rounded border border-slate-300 bg-white px-4 py-2 text-sm font-medium focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
            >
              Cancel
            </button>
          </div>
        </section>
      ) : null}

      {message ? <p role="status" className="text-sm text-emerald-700">{message}</p> : null}
      {create.isError ? (
        <p role="alert" className="text-sm text-red-600">
          {describeAdminError(create.error, "The rule could not be created.")}
        </p>
      ) : null}
      {update.isError ? (
        <p role="alert" className="text-sm text-red-600">
          {describeAdminError(update.error, "The rule could not be saved.")}
        </p>
      ) : null}

      <DangerActionDialog
        open={confirmOpen}
        title="Update business rule"
        description={
          editing
            ? `Change the configuration of ${editing.code}. The backend validates and audits the change.`
            : "Change the rule configuration."
        }
        confirmLabel="Save rule"
        busy={update.isPending}
        onConfirm={() => {
          if (!editing) {
            return;
          }
          try {
            update.mutate(
              { ruleId: editing.id, configuration: parseJsonObject(configText) },
              {
                onSuccess: () => {
                  setConfirmOpen(false);
                  setEditing(null);
                  setMessage("Business rule updated.");
                },
              },
            );
          } catch {
            setConfirmOpen(false);
            setConfigError("Enter a JSON object.");
          }
        }}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  );
}
