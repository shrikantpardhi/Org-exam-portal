"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";

import { useQuery } from "@tanstack/react-query";

import { getOrganizationName } from "@/features/administration/api/organization-config-api";
import { useAuth } from "@/lib/auth/auth-context";
import { roleHome } from "@/lib/auth/role-home";
import { ADMIN_MENU, SUPER_ADMIN_MENU, type ShellMenuSection } from "./sidebar-menu";
import { WorkspaceShell } from "./workspace-shell";

/**
 * The SUPER_ADMIN ({@code /platform/*}) and ADMIN ({@code /admin/*}) workspace shell
 * ({@code docs/frontend/navigation.md}).
 *
 * <p>Gate order: token → mustChangePassword → role → permission (per screen). Hidden menu items are a
 * UX affordance only — the backend remains authoritative.
 *
 * <p>Chrome lives in {@link WorkspaceShell} so the student workspace shares it instead of duplicating
 * it. This component only adds the two role-specific concerns: which menu, and which module gates.
 */
export function AppShell({
  role,
  children,
}: {
  role: "SUPER_ADMIN" | "ADMIN";
  children: ReactNode;
}) {
  const { status, user, can } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  const home = role === "SUPER_ADMIN" ? "/platform" : "/admin";

  const organizationName = useQuery({
    queryKey: ["admin", "organization-name"],
    queryFn: getOrganizationName,
    enabled: status === "authenticated" && role === "ADMIN",
    retry: false,
  });
  const orgName = role === "SUPER_ADMIN" ? "Platform" : (organizationName.data ?? null);

  // --- Gate: token → mustChangePassword → role ---
  let redirectTarget: string | null = null;
  if (status === "unauthenticated") {
    redirectTarget = `/login?next=${encodeURIComponent(pathname)}`;
  } else if (status === "authenticated" && user) {
    if (user.mustChangePassword && pathname !== "/change-password") {
      redirectTarget = "/change-password";
    } else if (!user.roles.includes(role)) {
      redirectTarget = roleHome(user.roles, false);
    }
  }

  useEffect(() => {
    if (redirectTarget) router.replace(redirectTarget);
  }, [redirectTarget, router]);

  // Computed before the early return: a hook must run on every render, including the redirect one.
  const menu: ShellMenuSection[] = (role === "SUPER_ADMIN" ? SUPER_ADMIN_MENU : ADMIN_MENU).map(
    (section) => ({
      ...section,
      items: section.items.filter((item) => !item.permission || can(item.permission)),
    }),
  );

  if (status === "loading" || redirectTarget) {
    return (
      <div role="status" className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">
        Loading…
      </div>
    );
  }

  return (
    <WorkspaceShell
      home={home}
      menu={menu}
      homeLabel={role === "SUPER_ADMIN" ? "Platform" : "Admin"}
      contextLabel={orgName}
    >
      {children}
    </WorkspaceShell>
  );
}
