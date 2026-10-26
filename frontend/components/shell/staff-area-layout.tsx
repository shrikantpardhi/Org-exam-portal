"use client";

import type { ReactNode } from "react";

import { EmptyState } from "@/components/ui/empty-state";
import { useAuth } from "@/lib/auth/auth-context";
import { AppShell } from "@/components/shell/app-shell";

/**
 * Staff content-area gate ({@code specs/20-P11 §63}).
 *
 * <p>The academic, content-authoring and staff-analytics routes were added under the app root rather
 * than beneath {@code /admin}, so they rendered outside both the shell and its guard: an
 * unauthenticated visitor got a page that rendered and then failed its queries, instead of being
 * sent to sign in. Wrapping them here gives them the admin workspace and its gate while leaving every
 * existing URL unchanged, so deep links and bookmarks keep working.
 *
 * <p>Holding *any* of the listed permissions is enough to enter the section; each screen inside it
 * still asks the backend for exactly what it needs. A hidden link is never the security boundary
 * ({@code §63}) — this only stops the obviously-wrong case of an anonymous visitor on a staff page.
 */
export function StaffAreaLayout({
  permissions,
  children,
}: {
  permissions: string[];
  children: ReactNode;
}) {
  const { status, can } = useAuth();

  if (status === "authenticated" && !permissions.some((permission) => can(permission))) {
    return (
      <AppShell role="ADMIN">
        <EmptyState
          title="You do not have access to this section"
          description="Your account is missing the permission this area requires. Ask an administrator if you need it."
        />
      </AppShell>
    );
  }

  return <AppShell role="ADMIN">{children}</AppShell>;
}
