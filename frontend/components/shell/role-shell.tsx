"use client";

import type { ReactNode } from "react";

import { AppShell } from "./app-shell";
import { StudentShell } from "./student-shell";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * Picks the workspace shell that matches the signed-in user's role.
 *
 * <p>Some routes are genuinely cross-role: `/account` and `/change-password` are visited by students,
 * administrators and the platform owner alike, and `/change-password` is the target of the
 * forced-password-change gate in every shell. Those pages therefore cannot live under a single role
 * layout without either duplicating the page three times or stripping their navigation.
 *
 * <p>This wrapper renders the shell the current user actually works in, so a student sees the student
 * sidebar on their account page and an administrator sees the admin sidebar on theirs — the same
 * layout discipline as the rest of the application, without moving any URL.
 */
export function RoleShell({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const roles = user?.roles ?? [];

  if (roles.includes("SUPER_ADMIN")) {
    return <AppShell role="SUPER_ADMIN">{children}</AppShell>;
  }
  if (roles.includes("ADMIN")) {
    return <AppShell role="ADMIN">{children}</AppShell>;
  }
  // STUDENT, and any role that only needs the base workspace.
  return <StudentShell>{children}</StudentShell>;
}
