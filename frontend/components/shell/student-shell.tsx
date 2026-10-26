"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";

import { useAuth } from "@/lib/auth/auth-context";
import { roleHome } from "@/lib/auth/role-home";
import { STUDENT_MENU } from "./sidebar-menu";
import { WorkspaceShell } from "./workspace-shell";

/**
 * The STUDENT workspace shell.
 *
 * <p>Before this existed, `/student/*` was ten independent pages that each rendered their own
 * `<main>`: no sidebar, no header, and no way to move between results, the store and notifications
 * without returning to the dashboard. The routes were all built; only the navigation between them
 * was missing.
 *
 * <p>The gate mirrors {@code AppShell}: token → mustChangePassword → role. The student menu is not a
 * permission gate, because a student's reads are scoped to the caller by the backend regardless of
 * what the UI shows ({@code specs/17-P8-Analytics-V1.5.md} §52).
 */
export function StudentShell({ children }: { children: ReactNode }) {
  const { status, user } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  let redirectTarget: string | null = null;
  if (status === "unauthenticated") {
    redirectTarget = `/login?next=${encodeURIComponent(pathname)}`;
  } else if (status === "authenticated" && user) {
    if (user.mustChangePassword && pathname !== "/change-password") {
      redirectTarget = "/change-password";
    } else if (!user.roles.includes("STUDENT")) {
      redirectTarget = roleHome(user.roles, false);
    }
  }

  useEffect(() => {
    if (redirectTarget) router.replace(redirectTarget);
  }, [redirectTarget, router]);

  if (status === "loading" || redirectTarget) {
    return (
      <div role="status" className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">
        Loading…
      </div>
    );
  }

  return (
    <WorkspaceShell home="/student" menu={STUDENT_MENU} homeLabel="Student">
      {children}
    </WorkspaceShell>
  );
}
