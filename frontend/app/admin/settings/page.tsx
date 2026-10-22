"use client";

import { AdminShell } from "@/features/administration/components/admin-shell";
import { SettingsScreen } from "@/features/administration/components/settings-screen";

/** Organization settings ({@code specs/20-P11-Administration-V1.5.md}). */
export default function AdminPage() {
  return (
    <AdminShell
      title="Organization settings"
      description="Typed values from the approved settings registry."
      permission="ORGANIZATION_SETTING_VIEW"
    >
      <SettingsScreen />
    </AdminShell>
  );
}
