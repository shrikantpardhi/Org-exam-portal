import { PlatformShell } from "@/features/platform/components/platform-shell";
import { SystemInfoScreen } from "@/features/platform/components/system-info-screen";

/** Platform system information ({@code docs/frontend/super-admin-flow.md} §11). */
export default function SystemInfoPage() {
  return (
    <PlatformShell
      title="System info"
      description="Version, build, migration and dependency health."
      permission="PLATFORM_SYSTEM_VIEW"
    >
      <SystemInfoScreen />
    </PlatformShell>
  );
}
