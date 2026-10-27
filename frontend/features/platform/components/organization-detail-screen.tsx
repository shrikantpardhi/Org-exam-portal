"use client";

import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";

import { Alert } from "@/components/ui/alert";
import { Button, ButtonLink } from "@/components/ui/button";
import { describeAdminError } from "@/components/admin/format";
import { StatusBadge } from "@/components/admin/status-badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { FormDialog } from "@/components/ui/form-dialog";
import { Select } from "@/components/ui/select";
import { useBreadcrumbLabel } from "@/components/shell/breadcrumbs";
import {
  OrganizationProfileFields,
  changedProfileValues,
  organizationProfileValues,
} from "@/features/platform/components/organization-profile-fields";
import {
  useChangeOrganizationStatus,
  useOrganization,
  useUpdateOrganization,
} from "@/features/platform/hooks/use-platform";
import {
  updateOrganizationSchema,
  type OrganizationDetail,
  type OrganizationStatus,
} from "@/features/platform/schemas/platform";

/**
 * A business organization detail with the administrator handover prompt
 * ({@code docs/frontend/super-admin-flow.md} §7-§9; V37, ADR-0051).
 *
 * <p>The platform owner needs to see the organization as it describes itself — its address, contact
 * point and logo — not just its code and row counts, because those are the details a support call is
 * about. Fields the organization has not filled in are shown as "Not set" rather than omitted, so an
 * incomplete profile is visible rather than silently absent.
 *
 * <p>The status badge is not decorative: the platform owner can suspend or reactivate the whole
 * tenant from here, and the dialog states the consequence before it is applied.
 */
export function OrganizationDetailScreen({ organizationId }: { organizationId: string }) {
  const query = useOrganization(organizationId);
  const searchParams = useSearchParams();
  const justCreated = searchParams.get("created") === "1";

  const [statusOpen, setStatusOpen] = useState(false);
  const [statusDraft, setStatusDraft] = useState<OrganizationStatus>("ACTIVE");
  const changeStatus = useChangeOrganizationStatus(organizationId);
  const [editOpen, setEditOpen] = useState(false);

  // The trail would otherwise show the raw uuid: the route knows the id, only this screen knows the
  // name. Nothing is registered while the organization is loading, which is fine — the uuid is what
  // is known at that point.
  useBreadcrumbLabel(organizationId, query.data?.name);

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

  /** Opens the status dialog seeded from the status the server currently reports. */
  const openStatusDialog = () => {
    setStatusDraft(org.status === "INACTIVE" ? "INACTIVE" : "ACTIVE");
    // A refusal from a previous attempt must not linger over a fresh attempt.
    changeStatus.reset();
    setStatusOpen(true);
  };

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
            <Button variant="outline" size="sm" onClick={() => setEditOpen(true)}>
              Edit profile
            </Button>
            <Button variant="outline" size="sm" onClick={openStatusDialog}>
              Change status
            </Button>
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
          <CardTitle>Administrators</CardTitle>
          <CardDescription>
            {org.adminCount === 0
              ? "This organization has no administrator yet. Create one to hand over administration."
              : "Manage this organization's administrators: correct their roles, transfer the primary designation, or archive one."}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {/*
            The primary administrator is named, not merely flagged as present: it is the only
            account that may edit roles (ADR-0052), so an operator troubleshooting "I cannot change
            roles" needs to read off who holds the designation, not just that somebody does.
          */}
          {org.primaryAdminEmail ? (
            <dl className="grid gap-x-8 gap-y-1 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-muted-foreground">Primary administrator</dt>
                <dd className="text-foreground">{org.primaryAdminEmail}</dd>
              </div>
            </dl>
          ) : (
            <Alert tone="warning" title="No primary administrator">
              No primary administrator is designated. Role editing is blocked until one is.
            </Alert>
          )}
          <div className="flex flex-wrap gap-2">
            <ButtonLink href={"/platform/organizations/" + organizationId + "/admins"}>
              {org.adminCount === 0 ? "Manage administrators" : "View administrators"}
            </ButtonLink>
            <ButtonLink
              variant="outline"
              href={"/platform/organizations/" + organizationId + "/admins/new"}
            >
              Invite administrator
            </ButtonLink>
          </div>
        </CardContent>
      </Card>

      {/*
        Mounted only while open: the form is seeded from the organization the server currently
        reports, so a dialog left mounted in the tree would keep the values it first captured and
        re-open showing a stale profile.
      */}
      {editOpen ? (
        <EditProfileDialog
          organization={org}
          onCancel={() => setEditOpen(false)}
          onSaved={() => setEditOpen(false)}
        />
      ) : null}

      <FormDialog
        open={statusOpen}
        title="Change organization status"
        description={
          org.status === "ACTIVE"
            ? "This organization is active. Choosing INACTIVE suspends it."
            : "This organization is inactive, so its users cannot sign in. Choosing ACTIVE restores access."
        }
        submitLabel="Change status"
        busy={changeStatus.isPending}
        onCancel={() => {
          if (!changeStatus.isPending) {
            setStatusOpen(false);
          }
        }}
        onSubmit={() => {
          changeStatus.mutate(statusDraft, {
            onSuccess: () => setStatusOpen(false),
          });
        }}
      >
        <div className="space-y-1">
          <label htmlFor="organization-status" className="text-sm font-medium text-foreground">
            Status
          </label>
          <Select
            id="organization-status"
            value={statusDraft}
            onChange={(event) => setStatusDraft(event.target.value as OrganizationStatus)}
          >
            <option value="ACTIVE">ACTIVE</option>
            <option value="INACTIVE">INACTIVE</option>
          </Select>
        </div>

        {/*
          The warning is shown for any selection that is not ACTIVE — that is the direction that
          locks people out. Reactivating does not need a warning, and warning about it would train
          the operator to dismiss the warning that matters.
        */}
        {statusDraft !== "ACTIVE" ? (
          <Alert tone="warning">
            Users of this organization will not be able to sign in while it is not active.
          </Alert>
        ) : null}

        {changeStatus.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {describeAdminError(changeStatus.error, "The organization status could not be changed.")}
          </p>
        ) : null}
      </FormDialog>
    </div>
  );
}

/**
 * The edit-profile dialog ({@code PATCH /platform/organizations/{id}}, ADR-0056).
 *
 * <p>Seeded from the organization as it is now, so the operator edits what exists rather than
 * re-typing it. Only the fields that differ from that seed are sent: the server treats an omitted
 * field as "leave it alone" and an empty string as "clear it", so sending the whole form would
 * rewrite every untouched column and make an untouched empty input ambiguous.
 *
 * <p>A failed save keeps the dialog open with the message inside it — closing on failure would throw
 * away what the operator typed — and the values they entered are still there to correct.
 */
function EditProfileDialog({
  organization,
  onCancel,
  onSaved,
}: {
  organization: OrganizationDetail;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const mutation = useUpdateOrganization(organization.id);
  const seeded = organizationProfileValues(organization);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Record<string, string>>({ defaultValues: seeded });

  const submit = handleSubmit(async (raw) => {
    const changed = changedProfileValues(seeded, raw);
    if (Object.keys(changed).length === 0) {
      // Nothing to send: a PATCH with an empty body is not a correction, and closing quietly is a
      // truer answer than a round trip that changes nothing.
      onSaved();
      return;
    }
    const parsed = updateOrganizationSchema.safeParse(changed);
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const path = issue.path[0];
        if (typeof path === "string") {
          setError(path, { message: issue.message });
        }
      }
      return;
    }
    try {
      await mutation.mutateAsync(parsed.data);
      onSaved();
    } catch {
      // Rendered from mutation.isError below: the dialog stays open on purpose.
    }
  });

  return (
    <FormDialog
      open
      title="Edit organization profile"
      description="Only the fields you change are saved. Clearing a field removes the value."
      submitLabel="Save profile"
      busy={isSubmitting || mutation.isPending}
      onCancel={() => {
        if (!mutation.isPending) {
          onCancel();
        }
      }}
      onSubmit={() => void submit()}
      className="max-h-[90vh] max-w-2xl overflow-y-auto"
    >
      <OrganizationProfileFields register={register} errors={errors} />

      {mutation.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {describeAdminError(mutation.error, "The organization profile could not be saved.")}
        </p>
      ) : null}
    </FormDialog>
  );
}
