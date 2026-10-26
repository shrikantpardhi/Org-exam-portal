"use client";

import { useEffect, useState } from "react";

import { describeAdminError } from "@/components/admin/format";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { OrganizationProfile } from "@/features/administration/api/organization-config-api";
import {
  useOrganizationProfile,
  useUpdateOrganizationProfile,
} from "@/features/administration/hooks/use-administration";
import { useAuth } from "@/lib/auth/auth-context";

type Draft = {
  name: string;
  description: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  stateRegion: string;
  postalCode: string;
  country: string;
  contactEmail: string;
  contactPhone: string;
  websiteUrl: string;
  logoUrl: string;
};

const EMPTY: Draft = {
  name: "",
  description: "",
  addressLine1: "",
  addressLine2: "",
  city: "",
  stateRegion: "",
  postalCode: "",
  country: "",
  contactEmail: "",
  contactPhone: "",
  websiteUrl: "",
  logoUrl: "",
};

function toDraft(profile: OrganizationProfile): Draft {
  return {
    name: profile.name,
    description: profile.description ?? "",
    addressLine1: profile.addressLine1 ?? "",
    addressLine2: profile.addressLine2 ?? "",
    city: profile.city ?? "",
    stateRegion: profile.stateRegion ?? "",
    postalCode: profile.postalCode ?? "",
    country: profile.country ?? "",
    contactEmail: profile.contactEmail ?? "",
    contactPhone: profile.contactPhone ?? "",
    websiteUrl: profile.websiteUrl ?? "",
    logoUrl: profile.logoUrl ?? "",
  };
}

/**
 * The organization profile editor ({@code specs/20-P11 §8}; V37, ADR-0051).
 *
 * <p>These are the organization's own identity details — title, description, address, contact point
 * and logo — as distinct from the typed configuration registry below, which controls behaviour.
 * Grouping them here means an administrator editing "what our organization is called" does not have
 * to scroll past maintenance-mode switches to find it.
 *
 * <p>Read-only for an administrator without {@code ORGANIZATION_SETTING_UPDATE}: the backend refuses
 * the write, and the form says so rather than failing on submit.
 */
export function OrganizationProfileCard() {
  const { can } = useAuth();
  const query = useOrganizationProfile();
  const update = useUpdateOrganizationProfile();
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [seeded, setSeeded] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (query.data && !seeded) {
      setDraft(toDraft(query.data));
      setSeeded(true);
    }
  }, [query.data, seeded]);

  const canUpdate = can("ORGANIZATION_SETTING_UPDATE");

  const field = (key: keyof Draft) => ({
    id: "org-" + key,
    value: draft[key],
    disabled: !canUpdate,
    onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      setDraft((current) => ({ ...current, [key]: event.target.value }));
      setSaved(false);
    },
    className:
      "w-full rounded-md border border-input bg-surface px-3 py-2 text-sm disabled:bg-muted disabled:text-muted-foreground",
  });

  if (query.isPending) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Organization details</CardTitle>
        </CardHeader>
        <CardContent>
          <p role="status" className="text-sm text-muted-foreground">Loading organization details…</p>
        </CardContent>
      </Card>
    );
  }

  if (query.isError) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Organization details</CardTitle>
        </CardHeader>
        <CardContent>
          <Alert tone="danger">
            {describeAdminError(query.error, "The organization details could not be loaded.")}
          </Alert>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Organization details</CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        <p className="text-sm text-muted-foreground">
          How this organization identifies itself to students and staff. The organization code
          ({query.data?.code}) is an identifier and is not editable here.
        </p>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1 sm:col-span-2">
            <label htmlFor="org-name" className="text-sm font-medium">Title</label>
            <input {...field("name")} required />
          </div>
          <div className="space-y-1 sm:col-span-2">
            <label htmlFor="org-description" className="text-sm font-medium">Description</label>
            <textarea {...field("description")} rows={3} />
          </div>
          <div className="space-y-1 sm:col-span-2">
            <label htmlFor="org-logoUrl" className="text-sm font-medium">Logo URL</label>
            <input {...field("logoUrl")} type="url" placeholder="https://example.test/logo.png" />
            <p className="text-xs text-muted-foreground">
              A link to a hosted image. Binary uploads are not part of this form.
            </p>
          </div>

          <div className="space-y-1 sm:col-span-2">
            <label htmlFor="org-addressLine1" className="text-sm font-medium">Address line 1</label>
            <input {...field("addressLine1")} />
          </div>
          <div className="space-y-1 sm:col-span-2">
            <label htmlFor="org-addressLine2" className="text-sm font-medium">Address line 2</label>
            <input {...field("addressLine2")} />
          </div>
          <div className="space-y-1">
            <label htmlFor="org-city" className="text-sm font-medium">City</label>
            <input {...field("city")} />
          </div>
          <div className="space-y-1">
            <label htmlFor="org-stateRegion" className="text-sm font-medium">State or region</label>
            <input {...field("stateRegion")} />
          </div>
          <div className="space-y-1">
            <label htmlFor="org-postalCode" className="text-sm font-medium">Postal code</label>
            <input {...field("postalCode")} />
          </div>
          <div className="space-y-1">
            <label htmlFor="org-country" className="text-sm font-medium">Country</label>
            <input {...field("country")} />
          </div>

          <div className="space-y-1">
            <label htmlFor="org-contactEmail" className="text-sm font-medium">Contact email</label>
            <input {...field("contactEmail")} type="email" />
          </div>
          <div className="space-y-1">
            <label htmlFor="org-contactPhone" className="text-sm font-medium">Contact phone</label>
            <input {...field("contactPhone")} type="tel" />
          </div>
          <div className="space-y-1 sm:col-span-2">
            <label htmlFor="org-websiteUrl" className="text-sm font-medium">Website</label>
            <input {...field("websiteUrl")} type="url" />
          </div>
        </div>

        {error ? <Alert tone="danger">{error}</Alert> : null}
        {update.isError ? (
          <Alert tone="danger">
            {describeAdminError(update.error, "The organization details could not be saved.")}
          </Alert>
        ) : null}
        {saved ? <Alert tone="success">Organization details saved.</Alert> : null}

        {canUpdate ? (
          <div className="flex flex-wrap gap-2">
            <Button
              onClick={() => {
                if (draft.name.trim().length === 0) {
                  setError("A title is required.");
                  return;
                }
                setError(null);
                update.mutate(draft, { onSuccess: () => setSaved(true) });
              }}
              disabled={update.isPending}
            >
              {update.isPending ? "Saving…" : "Save details"}
            </Button>
            <Button
              variant="ghost"
              onClick={() => {
                if (query.data) {
                  setDraft(toDraft(query.data));
                }
                setSaved(false);
                setError(null);
              }}
              disabled={update.isPending}
            >
              Reset
            </Button>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            You can view these details but not change them.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
