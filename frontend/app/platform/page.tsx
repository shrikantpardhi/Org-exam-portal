import { DashboardScreen } from "@/features/platform/components/dashboard-screen";
import { PlatformShell } from "@/features/platform/components/platform-shell";

/** SUPER_ADMIN platform dashboard ({@code docs/frontend/super-admin-flow.md} §3-§4). */
export default function PlatformDashboardPage() {
  return (
    <PlatformShell
      title="Dashboard"
      description="Platform-wide counts, system summary, and the first-organization prompt."
      permission="PLATFORM_SYSTEM_VIEW"
    >
      <DashboardScreen />
    </PlatformShell>
  );
}
