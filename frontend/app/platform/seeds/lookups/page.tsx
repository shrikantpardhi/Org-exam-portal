import { PlatformShell } from "@/features/platform/components/platform-shell";
import { SeedsLookupsScreen } from "@/features/platform/components/seeds-screens";

/** Seeded lookup master values ({@code docs/frontend/super-admin-flow.md} §14). */
export default function SeedsLookupsPage() {
  return (
    <PlatformShell
      title="Lookups"
      description="Reusable master values, read-only from the platform."
      permission="PLATFORM_SYSTEM_VIEW"
    >
      <SeedsLookupsScreen />
    </PlatformShell>
  );
}
