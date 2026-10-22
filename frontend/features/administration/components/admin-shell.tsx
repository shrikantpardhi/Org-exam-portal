"use client";

import type { ReactNode } from "react";

import { AdminPageHeader } from "@/components/admin/admin-page-header";
import { PermissionGuard } from "@/components/admin/permission-guard";
import { AdminNav } from "@/features/administration/components/admin-nav";

/**
 * The shared administration screen frame ({@code specs/20-P11-Administration-V1.5.md §122-§130}).
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
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-6 py-12">
      <AdminPageHeader title={title} description={description} actions={actions} />
      <AdminNav />
      <PermissionGuard permission={permission}>{children}</PermissionGuard>
    </main>
  );
}
