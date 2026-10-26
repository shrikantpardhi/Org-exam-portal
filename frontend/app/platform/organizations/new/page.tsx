import { OrganizationForm } from "@/features/platform/components/organization-form";
import { PlatformShell } from "@/features/platform/components/platform-shell";

/** Create a business organization ({@code docs/frontend/super-admin-flow.md} §6). */
export default function NewOrganizationPage() {
  return (
    <PlatformShell
      title="Create organization"
      description="Register a business organization with a unique code."
      permission="PLATFORM_ORGANIZATION_CREATE"
    >
      <OrganizationForm />
    </PlatformShell>
  );
}
