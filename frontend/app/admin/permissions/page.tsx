"use client";

import { AdminShell } from "@/features/administration/components/admin-shell";
import { PermissionsScreen } from "@/features/administration/components/permissions-screen";

/** Permissions ({@code specs/20-P11-Administration-V1.5.md}). */
export default function AdminPage() {
  return (
    <AdminShell
      title="Permissions"
      description="The read-only permission catalogue."
      permission="PERMISSION_VIEW"
    >
      <PermissionsScreen />
    </AdminShell>
  );
}
