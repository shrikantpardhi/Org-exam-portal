"use client";

import { AdminShell } from "@/features/administration/components/admin-shell";
import { RolesScreen } from "@/features/administration/components/roles-screen";

/** Roles ({@code specs/20-P11-Administration-V1.5.md}). */
export default function AdminPage() {
  return (
    <AdminShell
      title="Roles"
      description="Role administration and role-permission assignment."
      permission="ROLE_VIEW"
    >
      <RolesScreen />
    </AdminShell>
  );
}
