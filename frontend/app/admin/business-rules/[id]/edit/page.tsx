"use client";

import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { describeAdminError } from "@/components/admin/format";
import { Button, ButtonLink } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { AdminShell } from "@/features/administration/components/admin-shell";
import { explainRule } from "@/features/administration/lib/rule-explanations";
import {
  useBusinessRules,
  useUpdateBusinessRule,
} from "@/features/administration/hooks/use-administration";
import { parseJsonObject } from "@/features/administration/schemas/administration";

/**
 * Edit one business rule ({@code specs/20-P11 §71}).
 *
 * <p>A dedicated page rather than an inline panel on the list: the list is now the "browse and
 * understand" surface and this is the "change it deliberately" surface, so the explanation stays
 * visible while the operator edits and the Save action is unambiguous.
 *
 * <p>Delete is intentionally absent. The API exposes no DELETE for business rules
 * ({@code POST}, {@code GET}, {@code PUT} only), and inventing one would change a frozen contract.
 */
export default function EditBusinessRulePage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const query = useBusinessRules();
  const update = useUpdateBusinessRule();
  const rule = (query.data ?? []).find((candidate) => candidate.id === params.id);
  const [configText, setConfigText] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  // Seed the editor once the rule arrives, but never clobber what the operator has typed.
  const [seeded, setSeeded] = useState(false);
  useEffect(() => {
    if (rule && !seeded) {
      setConfigText(JSON.stringify(rule.configuration, null, 2));
      setSeeded(true);
    }
  }, [rule, seeded]);

  if (query.isPending) {
    return <p role="status" className="text-sm text-muted-foreground">Loading rule…</p>;
  }
  if (query.isError) {
    return (
      <p role="alert" className="text-sm text-destructive">
        {describeAdminError(query.error, "The business rule could not be loaded.")}
      </p>
    );
  }
  if (rule === undefined) {
    return (
      <AdminShell title="Rule not found" permission="BUSINESS_RULE_VIEW">
        <EmptyState
          title="Rule not found"
          description="This rule no longer exists, or it was removed since the list was loaded."
          action={
            <ButtonLink variant="outline" href={"/admin/business-rules"}>Back to business rules</ButtonLink>
          }
        />
      </AdminShell>
    );
  }

  const explanation = explainRule(rule.code);

  return (
    <AdminShell
      title={"Edit " + rule.code}
      description="The backend validates the shape and audits the change."
      permission="BUSINESS_RULE_VIEW"
      actions={
        <ButtonLink variant="outline" href={"/admin/business-rules/" + rule.id}>Cancel</ButtonLink>
      }
    >

      {explanation?.caution ? <Alert tone="warning">{explanation.caution}</Alert> : null}

      <Card>
        <CardHeader>
          <CardTitle>Configuration</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {explanation ? (
            <ul className="space-y-2 text-sm">
              {explanation.keys.map((entry) => (
                <li key={entry.key}>
                  <code className="font-mono text-xs text-foreground">{entry.key}</code>
                  <p className="text-muted-foreground">{entry.meaning}</p>
                </li>
              ))}
            </ul>
          ) : null}

          <div className="space-y-1">
            <label htmlFor="rule-configuration" className="text-sm font-medium">
              Configuration (JSON object)
            </label>
            <textarea
              id="rule-configuration"
              rows={10}
              value={configText}
              onChange={(event) => {
                setConfigText(event.target.value);
                setSaved(false);
              }}
              spellCheck={false}
              className="w-full rounded-md border border-input bg-surface px-3 py-2 font-mono text-sm"
            />
          </div>

          {error ? <Alert tone="danger">{error}</Alert> : null}
          {update.isError ? (
            <Alert tone="danger">{describeAdminError(update.error, "The rule could not be saved.")}</Alert>
          ) : null}
          {saved ? <Alert tone="success">Business rule updated.</Alert> : null}

          <div className="flex flex-wrap gap-2">
            <Button
              onClick={() => {
                let configuration;
                try {
                  configuration = parseJsonObject(configText);
                } catch {
                  setError("Enter a JSON object, for example { \"maxAdmins\": 1 }.");
                  return;
                }
                setError(null);
                update.mutate(
                  { ruleId: rule.id, configuration },
                  {
                    onSuccess: () => {
                      setSaved(true);
                      router.push("/admin/business-rules/" + rule.id);
                    },
                  },
                );
              }}
              disabled={update.isPending}
            >
              {update.isPending ? "Saving…" : "Save rule"}
            </Button>
            <ButtonLink variant="ghost" href={"/admin/business-rules"}>Back to business rules</ButtonLink>
          </div>
        </CardContent>
      </Card>
    </AdminShell>
  );
}
