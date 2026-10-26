"use client";

import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

import { RoleShell } from "@/components/shell/role-shell";

/**
 * Analytics routes ({@code specs/17-P8 §142}).
 *
 * <p>This tree is genuinely mixed: /analytics/student belongs to students, and
 * /analytics/{assessments,questions,topics} to staff. A role-specific shell would be wrong for one
 * half, so the tree uses RoleShell, which renders the workspace the signed-in user actually works in.
 *
 * <p>/analytics/organization is exempt: it is an administrative screen that supplies its own
 * AdminShell (so it gets the admin heading and permission frame), and nesting it inside RoleShell
 * would produce two sidebars and two main landmarks.
 */
export default function AnalyticsLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  if (pathname === "/analytics/organization") {
    return <>{children}</>;
  }
  return <RoleShell>{children}</RoleShell>;
}
