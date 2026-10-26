"use client";

import { useState } from "react";

import { CommerceForm } from "@/components/commerce/commerce-form";
import {
  CommerceEmpty,
  CommerceError,
  CommerceLoading,
  LifecycleBadge,
} from "@/components/commerce/commerce-states";
import { describeCommerceError, shortId } from "@/components/commerce/format";
import {
  addCollectionFormSchema,
  createPackageFormSchema,
} from "@/features/commerce/schemas/commerce";
import {
  useAddPackageCollection,
  useAllAccessPackages,
  useArchivePackage,
  useCreatePackage,
  usePackageCollections,
  useReconciliation,
  useRemovePackageCollection,
} from "@/features/commerce/hooks/use-commerce";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * Commerce administration ({@code specs/18-P9-Commerce-V1.5.md §86, §87}).
 *
 * <p>Packages are created and archived here; collections are mapped through the dedicated junction
 * API, so the bundle is always a persisted server relationship rather than a local selection.
 */
export function CommerceAdmin() {
  const { can } = useAuth();
  const packagesQuery = useAllAccessPackages();
  const createPackage = useCreatePackage();
  const archivePackage = useArchivePackage();
  const [selectedPackageId, setSelectedPackageId] = useState("");
  const collectionsQuery = usePackageCollections(selectedPackageId);
  const addCollection = useAddPackageCollection(selectedPackageId);
  const removeCollection = useRemovePackageCollection(selectedPackageId);
  const reconciliationQuery = useReconciliation();

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
      <section aria-labelledby="create-package-heading" className="space-y-3">
        <h2 id="create-package-heading" className="text-lg font-semibold">
          Create an access package
        </h2>
        <CommerceForm
          schema={createPackageFormSchema}
          submitLabel="Create package"
          fields={[
            { name: "code", label: "Code" },
            { name: "name", label: "Name" },
            { name: "description", label: "Description" },
          ]}
          onSubmit={async (values) => {
            await createPackage.mutateAsync({ ...values });
          }}
        />
      </section>

      <section aria-labelledby="package-admin-heading" className="space-y-3">
        <h2 id="package-admin-heading" className="text-lg font-semibold">
          Packages
        </h2>
        {packages.length === 0 ? (
          <CommerceEmpty message="No packages exist yet." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Access packages</caption>
              <thead>
                <tr className="border-b border-border">
                  <th scope="col" className="py-2 pr-3">Code</th>
                  <th scope="col" className="py-2 pr-3">Name</th>
                  <th scope="col" className="py-2 pr-3">Status</th>
                  <th scope="col" className="py-2">Actions</th>
                </tr>
              </thead>
              <tbody>
                {packages.map((pkg) => (
                  <tr key={pkg.id} className="border-b border-border">
                    <th scope="row" className="py-2 pr-3 font-mono text-xs">{pkg.code}</th>
                    <td className="py-2 pr-3">{pkg.name}</td>
                    <td className="py-2 pr-3"><LifecycleBadge status={pkg.status} /></td>
                    <td className="py-2">
                      <div className="flex flex-wrap gap-3">
                        <button
                          type="button"
                          onClick={() => setSelectedPackageId(pkg.id)}
                          className="rounded text-sm font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                        >
                          Manage collections
                        </button>
                        {pkg.status !== "ARCHIVED" ? (
                          <button
                            type="button"
                            onClick={() => {
                              archivePackage.mutate(pkg.id);
                            }}
                            className="rounded text-sm font-medium text-red-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-700"
                          >
                            Archive
                          </button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {archivePackage.isError ? (
          <CommerceError
            message={describeCommerceError(archivePackage.error, "The package could not be archived.")}
          />
        ) : null}
      </section>

      {selectedPackageId.length > 0 ? (
        <section aria-labelledby="collections-heading" className="space-y-3">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 id="collections-heading" className="text-lg font-semibold">
              Collection mapping
            </h2>
            <p className="font-mono text-xs text-muted-foreground">{shortId(selectedPackageId)}</p>
          </div>
          {collectionsQuery.isPending ? (
            <CommerceLoading label="Loading mappings…" />
          ) : collectionsQuery.isError ? (
            <CommerceError
              message={describeCommerceError(
                collectionsQuery.error,
                "Collection mappings could not be loaded.",
              )}
              onRetry={() => {
                void collectionsQuery.refetch();
              }}
            />
          ) : (collectionsQuery.data ?? []).length === 0 ? (
            <CommerceEmpty message="No collection mapping available." />
          ) : (
            <ul className="space-y-2 text-sm">
              {(collectionsQuery.data ?? []).map((mapping) => (
                <li
                  key={mapping.id}
                  className="flex flex-wrap items-center justify-between gap-2 rounded border border-border bg-surface px-3 py-2"
                >
                  <code className="text-xs" title={mapping.assessmentCollectionId}>
                    {mapping.assessmentCollectionId}
                  </code>
                  <button
                    type="button"
                    onClick={() => {
                      removeCollection.mutate(mapping.assessmentCollectionId);
                    }}
                    className="rounded text-sm font-medium text-red-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-700"
                  >
                    Remove mapping
                  </button>
                </li>
              ))}
            </ul>
          )}

          <CommerceForm
            schema={addCollectionFormSchema}
            submitLabel="Add collection"
            fields={[{ name: "collectionId", label: "Assessment collection id" }]}
            onSubmit={async (values) => {
              await addCollection.mutateAsync(values.collectionId);
            }}
          />
          {addCollection.isError ? (
            <CommerceError
              message={describeCommerceError(
                addCollection.error,
                "The collection could not be mapped.",
              )}
            />
          ) : null}
        </section>
      ) : null}

      {can("PURCHASE_VIEW") ? (
        <section aria-labelledby="reconciliation-heading" className="space-y-3">
          <h2 id="reconciliation-heading" className="text-lg font-semibold">
            Payment reconciliation
          </h2>
          {reconciliationQuery.isPending ? (
            <CommerceLoading label="Loading reconciliation…" />
          ) : reconciliationQuery.isError ? (
            <CommerceError
              message={describeCommerceError(
                reconciliationQuery.error,
                "Reconciliation could not be loaded.",
              )}
              onRetry={() => {
                void reconciliationQuery.refetch();
              }}
            />
          ) : reconciliationQuery.data ? (
            <>
              <p className="text-sm text-muted-foreground">
                {reconciliationQuery.data.checked} payment(s) checked,{" "}
                {reconciliationQuery.data.mismatches} mismatch(es).
              </p>
              {reconciliationQuery.data.findings.length === 0 ? (
                <CommerceEmpty message="No reconciliation findings." />
              ) : (
                <table className="w-full text-left text-sm">
                  <caption className="sr-only">Reconciliation findings</caption>
                  <thead>
                    <tr className="border-b border-border">
                      <th scope="col" className="py-2 pr-3">Reference</th>
                      <th scope="col" className="py-2 pr-3">Local</th>
                      <th scope="col" className="py-2 pr-3">Provider</th>
                      <th scope="col" className="py-2">State</th>
                    </tr>
                  </thead>
                  <tbody>
                    {reconciliationQuery.data.findings.map((finding, index) => (
                      <tr key={`${finding.paymentReference ?? "finding"}-${index}`} className="border-b border-border">
                        <th scope="row" className="py-2 pr-3 font-mono text-xs">
                          {finding.paymentReference ?? "Not available"}
                        </th>
                        <td className="py-2 pr-3">{finding.localStatus ?? "Not available"}</td>
                        <td className="py-2 pr-3">{finding.providerStatus ?? "Not available"}</td>
                        <td className="py-2">{finding.state ?? "Unknown"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}
