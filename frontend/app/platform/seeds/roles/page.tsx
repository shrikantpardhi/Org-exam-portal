import { PlatformShell } from "@/features/platform/components/platform-shell";
import { SeedsRolesScreen } from "@/features/platform/components/seeds-screens";

/** Seeded role catalogue ({@code docs/frontend/super-admin-flow.md} §12). */
export default function SeedsRolesPage() {
  return (
    <PlatformShell
      title="Roles"
      description="The seeded role catalogue, read-only from the platform."
      permission="PLATFORM_SYSTEM_VIEW"
    >
      <SeedsRolesScreen />
    </PlatformShell>
  );
}
