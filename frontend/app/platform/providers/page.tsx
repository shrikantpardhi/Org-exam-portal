import { PlatformShell } from "@/features/platform/components/platform-shell";
import { ProvidersScreen } from "@/features/platform/components/providers-screen";

/** Read-only provider configuration ({@code docs/frontend/super-admin-flow.md} §10). */
export default function ProvidersPage() {
  return (
    <PlatformShell
      title="Providers"
      description="Masked email and payment provider configuration. Secrets are never shown."
      permission="PLATFORM_PROVIDER_VIEW"
    >
      <ProvidersScreen />
    </PlatformShell>
  );
}
