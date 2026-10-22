"use client";

import { AdminShell } from "@/features/administration/components/admin-shell";
import { DashboardScreen } from "@/features/administration/components/dashboard-screen";

/** Dashboard ({@code specs/20-P11-Administration-V1.5.md}). */
export default function AdminPage() {
  return (
    <AdminShell
      title="Dashboard"
      description="Aggregated operational cards from the owning modules."
      permission="ADMIN_DASHBOARD_VIEW"
    >
      <DashboardScreen />
    </AdminShell>
  );
}
