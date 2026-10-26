import { PlatformShell } from "@/features/platform/components/platform-shell";
import { SeedsPermissionsScreen } from "@/features/platform/components/seeds-screens";

/** Seeded permission catalogue ({@code docs/frontend/super-admin-flow.md} §13). */
export default function SeedsPermissionsPage() {
  return (
    <PlatformShell
      title="Permissions"
      description="The seeded permission catalogue, read-only from the platform."
      permission="PLATFORM_SYSTEM_VIEW"
    >
      <SeedsPermissionsScreen />
    </PlatformShell>
  );
}
