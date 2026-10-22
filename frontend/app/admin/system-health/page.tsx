"use client";

import { AdminShell } from "@/features/administration/components/admin-shell";
import { SystemHealthScreen } from "@/features/administration/components/system-health-screen";

/** System health ({@code specs/20-P11-Administration-V1.5.md}). */
export default function AdminPage() {
  return (
    <AdminShell
      title="System health"
      description="Aggregated component health without secrets or stack traces."
      permission="SYSTEM_HEALTH_VIEW"
    >
      <SystemHealthScreen />
    </AdminShell>
  );
}
