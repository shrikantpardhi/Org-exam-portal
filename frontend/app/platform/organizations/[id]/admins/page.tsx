import { OrganizationAdminsScreen } from "@/features/platform/components/organization-admins-screen";
import { PlatformShell } from "@/features/platform/components/platform-shell";

/** Organization administrator management (ADR-0053, ADR-0054). */
export default async function OrganizationAdminsPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <PlatformShell
      title="Organization administrators"
      description="Correct roles, transfer the primary designation, archive or restore."
      permission="PLATFORM_ADMIN_VIEW"
    >
      <OrganizationAdminsScreen organizationId={id} />
    </PlatformShell>
  );
}
