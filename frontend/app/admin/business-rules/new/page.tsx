"use client";

import { useRouter } from "next/navigation";

import { describeAdminError } from "@/components/admin/format";
import { Alert } from "@/components/ui/alert";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AdminShell } from "@/features/administration/components/admin-shell";
import { useCreateBusinessRule } from "@/features/administration/hooks/use-administration";
import { parseJsonObject } from "@/features/administration/schemas/administration";
import { documentedRules } from "@/features/administration/lib/rule-explanations";
import { useState } from "react";

/**
 * Create a business rule ({@code specs/20-P11 §71}).
 *
 * <p>The documented rules are offered as starting points because their configuration shape is
 * enforced by real code; starting from a known-good document is safer than an empty textarea. An
 * arbitrary code can still be entered — the backend validates it.
 */
export default function NewBusinessRulePage() {
  const router = useRouter();
  const create = useCreateBusinessRule();
  const [code, setCode] = useState("");
  const [configText, setConfigText] = useState("{\n  \"maxAdmins\": 1\n}");
  const [error, setError] = useState<string | null>(null);

  return (
    <AdminShell
      title="Create a business rule"
      description="A rule is a named JSON document that a module reads to make a decision."
      permission="BUSINESS_RULE_VIEW"
      actions={
        <ButtonLink variant="outline" href={"/admin/business-rules"}>Cancel</ButtonLink>
      }
    >

      <Card>
        <CardHeader>
          <CardTitle>Rule</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-1">
            <label htmlFor="rule-code" className="text-sm font-medium">Code</label>
            <input
              id="rule-code"
              value={code}
              onChange={(event) => setCode(event.target.value)}
              list="documented-rule-codes"
              placeholder="MAX_ADMINS_PER_ORG"
              className="w-full rounded-md border border-input bg-surface px-3 py-2 font-mono text-sm"
            />
            <datalist id="documented-rule-codes">
              {documentedRules().map((rule) => (
                <option key={rule.code} value={rule.code}>{rule.title}</option>
              ))}
            </datalist>
          </div>

          <div className="space-y-1">
            <label htmlFor="rule-configuration-new" className="text-sm font-medium">
              Configuration (JSON object)
            </label>
            <textarea
              id="rule-configuration-new"
              rows={10}
              value={configText}
              onChange={(event) => setConfigText(event.target.value)}
              spellCheck={false}
              className="w-full rounded-md border border-input bg-surface px-3 py-2 font-mono text-sm"
            />
          </div>

          {error ? <Alert tone="danger">{error}</Alert> : null}
          {create.isError ? (
            <Alert tone="danger">{describeAdminError(create.error, "The rule could not be created.")}</Alert>
          ) : null}

          <div className="flex flex-wrap gap-2">
            <Button
              onClick={() => {
                if (code.trim().length === 0) {
                  setError("A rule code is required.");
                  return;
                }
                let configuration;
                try {
                  configuration = parseJsonObject(configText);
                } catch {
                  setError("Enter a JSON object.");
                  return;
                }
                setError(null);
                create.mutate(
                  { code: code.trim(), configuration },
                  { onSuccess: () => router.push("/admin/business-rules") },
                );
              }}
              disabled={create.isPending}
            >
              {create.isPending ? "Creating…" : "Create rule"}
            </Button>
          </div>
        </CardContent>
      </Card>
    </AdminShell>
  );
}
