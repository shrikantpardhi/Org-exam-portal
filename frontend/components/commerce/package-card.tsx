"use client";

import Link from "next/link";

import type { AccessPackage } from "@/features/commerce/schemas/commerce";

import { LifecycleBadge } from "./commerce-states";

/** A catalog package ({@code specs/18-P9-Commerce-V1.5.md §77}). */
export function PackageCard({ pkg, href }: { pkg: AccessPackage; href?: string }) {
  return (
    <article aria-label={pkg.name} className="flex h-full flex-col rounded-lg border border-border bg-surface p-4 shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <p className="font-mono text-xs text-muted-foreground">{pkg.code}</p>
        <LifecycleBadge status={pkg.status} />
      </div>
      <h3 className="mt-2 text-base font-semibold text-foreground">{pkg.name}</h3>
      <p className="mt-1 flex-1 text-sm text-muted-foreground">{pkg.description ?? "No description provided."}</p>
      {href ? (
        <Link
          href={href}
          className="mt-4 inline-block rounded text-sm font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          View package
        </Link>
      ) : null}
    </article>
  );
}
