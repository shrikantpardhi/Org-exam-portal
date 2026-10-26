"use client";

import { describeAdminError, formatDateTime } from "@/components/admin/format";
import { StatusBadge } from "@/components/admin/status-badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useProviders } from "@/features/platform/hooks/use-platform";

function providerState(configured: boolean): string {
  return configured ? "UP" : "UNKNOWN";
}

/** Read-only masked provider configuration (super-admin-flow §10). Never shows secrets. */
export function ProvidersScreen() {
  const query = useProviders();

  if (query.isPending) {
    return <p role="status" className="text-sm text-muted-foreground">Loading providers…</p>;
  }

  if (query.isError) {
    return (
      <div role="alert" className="rounded-lg border border-destructive bg-destructive-subtle p-4 text-sm text-destructive">
        {describeAdminError(query.error, "Providers could not be loaded.")}
        <button type="button" onClick={() => void query.refetch()} className="ml-3 underline">
          Retry
        </button>
      </div>
    );
  }

  const providers = query.data;

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <CardTitle>Email provider</CardTitle>
            <StatusBadge status={providerState(providers.email.configured)} />
          </div>
          <CardDescription>Outbound email delivery.</CardDescription>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-y-2 text-sm">
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Provider</dt>
              <dd>{providers.email.providerName ?? "Not configured"}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">From address</dt>
              <dd>{providers.email.fromAddress ?? "—"}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Updated</dt>
              <dd>{formatDateTime(providers.email.updatedAt) ?? "—"}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <CardTitle>Payment provider</CardTitle>
            <StatusBadge status={providerState(providers.payment.configured)} />
          </div>
          <CardDescription>Payment processing.</CardDescription>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-y-2 text-sm">
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Provider</dt>
              <dd>{providers.payment.providerName ?? "Not configured"}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Webhook secret</dt>
              <dd>{providers.payment.webhookSecretSet ? "Set" : "Not set"}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Updated</dt>
              <dd>{formatDateTime(providers.payment.updatedAt) ?? "—"}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  );
}
