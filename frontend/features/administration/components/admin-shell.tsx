"use client";

import type { ReactNode } from "react";

import { AdminPageHeader } from "@/components/admin/admin-page-header";
import { PermissionGuard } from "@/components/admin/permission-guard";

/**
 * The shared administration screen frame (docs/frontend/navigation.md).
 *
 * The AppShell already renders the sidebar, header and breadcrumbs; this frame adds the page
 * heading and the per-screen permission guard. The guard is a UX affordance only — the backend
 * independently enforces the same permission on every request.
 */
export function AdminShell({
  title,
  description,
  permission,
  actions,
  children,
}: {
  title: string;
  description?: string;
  permission: string;
  actions?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6">
      <AdminPageHeader title={title} description={description} actions={actions} />
      <PermissionGuard permission={permission}>{children}</PermissionGuard>
    </div>
  );
}
