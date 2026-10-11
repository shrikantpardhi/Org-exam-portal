"use client";

import type { ReactNode } from "react";

import { useAuth } from "@/lib/auth/auth-context";

/**
 * Protected UI boundary ({@code specs/10-P1-Identity-V1.5.md §103, §162}).
 *
 * <p>UX behaviour only: it checks authentication state, loads current user, and renders or denies.
 * The backend remains the security boundary. An optional permission gates a UI affordance; hiding
 * a control is never authorization.
 */
export function AuthenticatedGuard({
  children,
  permission,
}: {
  children: ReactNode;
  permission?: string;
}) {
  const { status, can } = useAuth();

  if (status === "loading") {
    return (
      <p role="status" aria-live="polite" className="text-sm text-slate-500">
        Checking your session…
      </p>
    );
  }

  if (status !== "authenticated") {
    return (
      <p role="alert" className="text-sm text-red-600">
        You must sign in to view this page.
      </p>
    );
  }

  if (permission && !can(permission)) {
    return (
      <p role="alert" className="text-sm text-red-600">
        You do not have access to this page.
      </p>
    );
  }

  return <>{children}</>;
}
