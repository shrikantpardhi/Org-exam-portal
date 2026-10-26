"use client";

import Link from "next/link";

import { useAuth } from "@/lib/auth/auth-context";

/**
 * A permission-gated entry point into a staff analytics screen
 * ({@code specs/17-P8-Analytics-V1.5.md §148}).
 *
 * <p>The link is hidden when the caller lacks the permission, but hiding it is only an affordance:
 * the analytics endpoint independently enforces {@code ANALYTICS_VIEW}.
 */
export function AnalyticsLink({ href, label }: { href: string; label: string }) {
  const { can, status } = useAuth();

  if (status !== "authenticated" || !can("ANALYTICS_VIEW")) {
    return null;
  }

  return (
    <Link
      href={href}
      className="inline-block rounded text-sm font-medium text-info underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
    >
      {label}
    </Link>
  );
}
