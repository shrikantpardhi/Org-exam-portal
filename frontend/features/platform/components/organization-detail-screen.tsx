"use client";

import { useSearchParams } from "next/navigation";

import { Alert } from "@/components/ui/alert";
import { ButtonLink } from "@/components/ui/button";
import { describeAdminError } from "@/components/admin/format";
import { StatusBadge } from "@/components/admin/status-badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useOrganization } from "@/features/platform/hooks/use-platform";

/**
 * A business organization detail with the one-ADMIN handover prompt
 * ({@code docs/frontend/super-admin-flow.md} §7-§9; V37, ADR-0051).
 *
 * <p>The platform owner needs to see the organization as it describes itself — its address, contact
 * point and logo — not just its code and row counts, because those are the details a support call is
 * about. Fields the organization has not filled in are shown as "Not set" rather than omitted, so an
 * incomplete profile is visible rather than silently absent.
 */
export function OrganizationDetailScreen({ organizationId }: { organizationId: string }) {
  const query = useOrganization(organizationId);
  const searchParams = useSearchParams();
  const justCreated = searchParams.get("created") === "1";

  if (query.isPending) {
    return <p role="status" className="text-sm text-muted-foreground">Loading organization…</p>;
  }

  if (query.isError) {
    return (
      <div role="alert" className="rounded-lg border border-destructive bg-destructive-subtle p-4 text-sm text-destructive">
        {describeAdminError(query.error, "The organization could not be loaded.")}
        <button type="button" onClick={() => void query.refetch()} className="ml-3 underline">
          Retry
        </button>
      </div>
    );
  }

  const org = query.data;

  const profile: { label: string; value: string | null | undefined }[] = [
    { label: "Title", value: org.name },
    { label: "Code", value: org.code },
    { label: "Description", value: org.description },
    { label: "Address line 1", value: org.addressLine1 },
    { label: "Address line 2", value: org.addressLine2 },
    { label: "City", value: org.city },
    { label: "State or region", value: org.stateRegion },
    { label: "Postal code", value: org.postalCode },
    { label: "Country", value: org.country },
    { label: "Contact email", value: org.contactEmail },
    { label: "Contact phone", value: org.contactPhone },
    { label: "Website", value: org.websiteUrl },
  ];

  return (
    <div className="space-y-6">
      {justCreated ? (
        <Alert tone="success" title="Organization created">
          One step remains: create this organization&apos;s administrator below so it can be handed over.
        </Alert>
      ) : null}

      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-center gap-2">
            <CardTitle>{org.name}</CardTitle>
            <StatusBadge status={org.status} />
          </div>
          <CardDescription>{org.code}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <dl className="grid gap-x-8 gap-y-3 text-sm sm:grid-cols-2">
            {profile.map((field) => (
              <div key={field.label}>
                <dt className="text-muted-foreground">{field.label}</dt>
                <dd className="text-foreground">
                  {field.value && field.value.length > 0 ? field.value : "Not set"}
                </dd>
              </div>
            ))}
          </dl>

          {org.logoUrl ? (
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">Logo</p>
              {/* A plain <img> with a fixed box: the URL is operator-supplied, and next/image would
                  add an optimisation step for a single small asset the platform does not host. */}
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={org.logoUrl}
                alt={"Logo for " + org.name}
                className="h-16 w-16 rounded border border-border object-contain"
              />
            </div>
          ) : null}

          <dl className="grid gap-x-8 gap-y-3 border-t border-border pt-4 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-muted-foreground">Administrators</dt>
              <dd className="text-foreground">{org.adminCount}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Users</dt>
              <dd className="text-foreground">{org.userCount}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Administrator</CardTitle>
          <CardDescription>
            {org.adminCount === 0
              ? "This organization has no administrator yet. Create one to hand over administration."
              : "Each organization accepts exactly one ADMIN. The account already exists."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {org.adminCount === 0 ? (
            <ButtonLink href={"/platform/organizations/" + organizationId + "/admins/new"}>
              Create administrator
            </ButtonLink>
          ) : (
            <p className="text-sm text-muted-foreground">
              Use the platform user list to recover that administrator&apos;s access if it is lost.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
