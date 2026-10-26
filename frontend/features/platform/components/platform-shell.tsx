"use client";

import type { ReactNode } from "react";

import { PermissionGuard } from "@/components/admin/permission-guard";
import { PlatformPageHeader } from "./platform-page-header";

/**
 * The shared platform screen frame (docs/frontend/super-admin-flow.md).
 *
 * The AppShell already renders the sidebar, header and breadcrumbs; this frame adds the platform
 * page heading and the per-screen permission guard. The guard is a UX affordance only — the
 * backend independently enforces the same permission on every request.
 */
export function PlatformShell({
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
      <PlatformPageHeader title={title} description={description} actions={actions} />
      <PermissionGuard permission={permission}>{children}</PermissionGuard>
    </div>
  );
}
