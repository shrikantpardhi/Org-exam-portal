"use client";

import { useState, type FormEvent } from "react";

import { AccessGrantCard } from "@/components/commerce/access-grant-card";
import { CommerceForm } from "@/components/commerce/commerce-form";
import {
  CommerceEmpty,
  CommerceError,
  CommerceLoading,
} from "@/components/commerce/commerce-states";
import { describeCommerceError } from "@/components/commerce/format";
import { createAccessGrantFormSchema } from "@/features/commerce/schemas/commerce";
import {
  useAccessGrants,
  useAccessPackages,
  useCreateAccessGrant,
} from "@/features/commerce/hooks/use-commerce";

/**
 * Manual access grant workspace ({@code specs/18-P9-Commerce-V1.5.md §88}).
 *
 * <p>The operator chooses the user, the package and the validity window; the server validates and
 * audits the grant. The UI does not merge or infer overlapping grants — that is a business policy
 * the backend owns.
 */
export function AccessGrantAdmin() {
  const [draftUserId, setDraftUserId] = useState("");
  const [userId, setUserId] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const grantsQuery = useAccessGrants(userId);
  const packagesQuery = useAccessPackages("ACTIVE");
  const create = useCreateAccessGrant();

  const search = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setUserId(draftUserId.trim());
  };

  if (packagesQuery.isPending) {
    return <CommerceLoading label="Loading packages…" />;
  }

  if (packagesQuery.isError) {
    return (
      <CommerceError
        message={describeCommerceError(packagesQuery.error, "Packages could not be loaded.")}
        onRetry={() => {
          void packagesQuery.refetch();
        }}
      />
    );
  }

  const packages = packagesQuery.data ?? [];

  return (
    <div className="space-y-8">
      <form onSubmit={search} className="flex flex-wrap items-end gap-2" aria-label="Find a user's grants">
        <div className="flex-1 space-y-1">
          <label htmlFor="grant-user-filter" className="text-sm font-medium">
            User id
          </label>
          <input
            id="grant-user-filter"
            value={draftUserId}
            onChange={(event) => setDraftUserId(event.target.value)}
            placeholder="Paste a user UUID"
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <button
          type="submit"
          className="rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-900"
        >
          Load grants
        </button>
      </form>

      <section aria-labelledby="grant-list-heading" className="space-y-3">
        <h2 id="grant-list-heading" className="text-lg font-semibold">
          Access grants
        </h2>
        {userId.length === 0 ? (
          <p className="text-sm text-slate-500">
            Enter a user id to review that user&apos;s grants. Staff visibility is limited by the
            backend permission and scope.
          </p>
        ) : grantsQuery.isPending ? (
          <CommerceLoading label="Loading access grants…" />
        ) : grantsQuery.isError ? (
          <CommerceError
            message={describeCommerceError(grantsQuery.error, "Access grants could not be loaded.")}
            onRetry={() => {
              void grantsQuery.refetch();
            }}
          />
        ) : (grantsQuery.data ?? []).length === 0 ? (
          <CommerceEmpty message="No access grants for this user." />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            {(grantsQuery.data ?? []).map((grant) => (
              <AccessGrantCard key={grant.id} grant={grant} />
            ))}
          </div>
        )}
      </section>

      <section aria-labelledby="create-grant-heading" className="space-y-3">
        <h2 id="create-grant-heading" className="text-lg font-semibold">
          Create a manual grant
        </h2>
        <CommerceForm
          schema={createAccessGrantFormSchema}
          submitLabel="Create grant"
          fields={[
            { name: "userId", label: "User id" },
            {
              name: "accessPackageId",
              label: "Access package",
              type: "select",
              options: packages.map((pkg) => ({ value: pkg.id, label: `${pkg.name} (${pkg.code})` })),
            },
            { name: "startDate", label: "Starts", type: "datetime-local" },
            { name: "endDate", label: "Ends (optional)", type: "datetime-local" },
          ]}
          onSubmit={async (values) => {
            await create.mutateAsync({ ...values });
            setMessage("Access grant created.");
            setUserId(values.userId);
          }}
        />
        {message ? (
          <p role="status" className="text-sm text-emerald-700">
            {message}
          </p>
        ) : null}
      </section>
    </div>
  );
}
