import { DashboardScreen } from "@/features/administration/components/dashboard-screen";
import { AdminShell } from "@/features/administration/components/admin-shell";

/** The administration entry point ({@code docs/frontend/navigation.md}). */
export default function AdminHomePage() {
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
