"use client";

import type { ReactNode } from "react";

import { useAuth } from "@/lib/auth/auth-context";

/**
 * A UI affordance boundary ({@code specs/20-P11-Administration-V1.5.md §67}).
 *
 * <p>Hiding or denying a section here is not authorization: the backend independently enforces the
 * same permission on every request. The guard prevents a dead screen, it does not secure one.
 */
export function PermissionGuard({
  permission,
  children,
  fallback,
}: {
  permission: string;
  children: ReactNode;
  fallback?: ReactNode;
}) {
  const { can, status } = useAuth();

  if (status === "loading") {
    return (
      <p role="status" aria-live="polite" className="text-sm text-muted-foreground">
        Checking your permissions…
      </p>
    );
  }

  if (!can(permission)) {
    return (
      fallback ?? (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          You do not have access to this section.
        </div>
      )
    );
  }

  return <>{children}</>;
}
