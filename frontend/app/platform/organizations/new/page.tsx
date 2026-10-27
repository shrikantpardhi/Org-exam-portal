import { OrganizationForm } from "@/features/platform/components/organization-form";
import { PlatformShell } from "@/features/platform/components/platform-shell";

/**
 * Create a business organization ({@code docs/frontend/super-admin-flow.md} §6).
 *
 * <p>The description states the one thing the form itself cannot: only the code and the name are
 * required, so an operator who does not know the address yet can proceed without guessing.
 */
export default function NewOrganizationPage() {
  return (
    <PlatformShell
      title="Create organization"
      description="Register a tenant and its profile. Only the code and the name are required; the address and contact details can be added later."
      permission="PLATFORM_ORGANIZATION_CREATE"
    >
      <OrganizationForm />
    </PlatformShell>
  );
}
