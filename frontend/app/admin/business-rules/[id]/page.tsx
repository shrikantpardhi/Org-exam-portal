"use client";

import { useParams } from "next/navigation";

import { Badge } from "@/components/ui/badge";
import { ButtonLink } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { describeAdminError } from "@/components/admin/format";
import { AdminShell } from "@/features/administration/components/admin-shell";
import { explainRule } from "@/features/administration/lib/rule-explanations";
import { useBusinessRules } from "@/features/administration/hooks/use-administration";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * Read-only view of one business rule ({@code specs/20-P11 §9, §71}).
 *
 * <p>Shown before any edit: an operator should understand what a rule enforces before changing it.
 * The explanation comes from a registry written against the enforcing code, not from the rule name.
 * An unrecognised rule still renders — with its raw configuration and an explicit note that no
 * explanation is documented — rather than showing a guess.
 */
export default function BusinessRuleViewPage() {
  const params = useParams<{ id: string }>();
  const { can } = useAuth();
  const query = useBusinessRules();
  const rule = (query.data ?? []).find((candidate) => candidate.id === params.id);
  const explanation = rule ? explainRule(rule.code) : undefined;

  return (
    <AdminShell
      title={explanation?.title ?? rule?.code ?? "Business rule"}
      description={
        explanation?.purpose ?? "No plain-language explanation is documented for this rule."
      }
      permission="BUSINESS_RULE_VIEW"
      actions={
        <div className="flex flex-wrap gap-2">
          <ButtonLink variant="outline" href={"/admin/business-rules"}>Back to business rules</ButtonLink>
          {rule && can("BUSINESS_RULE_UPDATE") ? (
            <ButtonLink href={"/admin/business-rules/" + rule.id + "/edit"}>Edit rule</ButtonLink>
          ) : null}
        </div>
      }
    >
      {query.isPending ? (
        <p role="status" className="text-sm text-muted-foreground">Loading rule…</p>
      ) : null}

      {query.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {describeAdminError(query.error, "The business rule could not be loaded.")}
        </p>
      ) : null}

      {query.isSuccess && !rule ? (
        <EmptyState
          title="Rule not found"
          description="This rule no longer exists, or it was removed since the list was loaded."
          action={
            <ButtonLink variant="outline" href={"/admin/business-rules"}>Back to business rules</ButtonLink>
          }
        />
      ) : null}

      {rule ? (
        <div className="flex flex-col gap-6">
          <Card>
            <CardHeader>
              <CardTitle>What this rule does</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4 text-sm">
              <dl className="grid gap-4 sm:grid-cols-2">
                <div>
                  <dt className="text-muted-foreground">Code</dt>
                  <dd className="font-mono text-xs text-foreground">{rule.code}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Status</dt>
                  <dd>
                    <Badge variant={rule.status === "ACTIVE" ? "success" : "neutral"}>{rule.status}</Badge>
                  </dd>
                </div>
              </dl>

              {explanation ? (
                <>
                  <div>
                    <dt className="text-muted-foreground">Enforcement</dt>
                    <dd className="text-foreground">{explanation.enforcedBy}</dd>
                  </div>
                  {explanation.caution ? (
                    <p className="rounded-md border border-warning bg-warning-subtle p-3 text-sm text-foreground">
                      {explanation.caution}
                    </p>
                  ) : null}
                </>
              ) : (
                <p className="rounded-md border border-border bg-muted p-3 text-muted-foreground">
                  This rule is enforced by the backend, but no plain-language explanation is documented
                  in the administration UI. Read the configuration and the module documentation before
                  changing it.
                </p>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Configuration</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {explanation ? (
                <ul className="space-y-3 text-sm">
                  {explanation.keys.map((entry) => (
                    <li key={entry.key}>
                      <code className="font-mono text-xs text-foreground">{entry.key}</code>
                      <p className="text-muted-foreground">{entry.meaning}</p>
                    </li>
                  ))}
                </ul>
              ) : null}
              <pre className="overflow-x-auto rounded-md border border-border bg-muted p-3 text-xs">
                <code>{JSON.stringify(rule.configuration, null, 2)}</code>
              </pre>
            </CardContent>
          </Card>
        </div>
      ) : null}
    </AdminShell>
  );
}
