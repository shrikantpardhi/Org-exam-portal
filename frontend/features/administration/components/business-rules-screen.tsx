"use client";

import Link from "next/link";

import { DataTable, type DataTableColumn } from "@/components/admin/data-table";
import { StatusBadge } from "@/components/admin/status-badge";
import { describeAdminError } from "@/components/admin/format";
import type { BusinessRule } from "@/features/administration/schemas/administration";
import { useBusinessRules } from "@/features/administration/hooks/use-administration";
import { explainRule } from "@/features/administration/lib/rule-explanations";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * The business-rule list ({@code specs/20-P11-Administration-V1.5.md} §9, §71).
 *
 * <p>This screen is now a browse-and-understand surface. Creating and editing happen on their own
 * pages, because a rule changes how the platform behaves and deserves a screen where the operator can
 * read what it enforces while changing it. Every row explains itself: a rule whose purpose is not
 * obvious from its code is a rule an administrator will get wrong.
 */
export function BusinessRulesScreen() {
  const { can } = useAuth();
  const query = useBusinessRules();

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
      key: "purpose",
      header: "What it does",
      render: (rule) => {
        const explanation = explainRule(rule.code);
        return (
          <span className="block max-w-md text-xs text-muted-foreground">
            {explanation?.purpose ?? "No plain-language explanation is documented for this rule."}
          </span>
        );
      },
    },
    {
      key: "actions",
      header: "Actions",
      render: (rule) => (
        <div className="flex flex-wrap items-center gap-3">
          {/* View comes first: understanding a rule must not require edit rights. */}
          <Link
            href={"/admin/business-rules/" + rule.id}
            className="text-sm font-medium text-foreground underline underline-offset-2 hover:text-primary"
          >
            View
          </Link>
          {can("BUSINESS_RULE_UPDATE") ? (
            <Link
              href={"/admin/business-rules/" + rule.id + "/edit"}
              className="text-sm font-medium text-foreground underline underline-offset-2 hover:text-primary"
            >
              Edit
            </Link>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <p className="max-w-2xl text-sm text-muted-foreground">
          A business rule is a named JSON document that a module reads to make a decision. Use view
          before editing: it explains what the rule enforces and what each key means.
        </p>
        {can("BUSINESS_RULE_CREATE") ? (
          <Link
            href="/admin/business-rules/new"
            className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary-hover"
          >
            Add rule
          </Link>
        ) : null}
      </div>

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

    </div>
  );
}
