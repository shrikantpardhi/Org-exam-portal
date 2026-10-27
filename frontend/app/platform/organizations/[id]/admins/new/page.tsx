import { CreateAdminForm } from "@/features/platform/components/create-admin-form";
import { PlatformShell } from "@/features/platform/components/platform-shell";

/** Create the organization ADMIN ({@code docs/frontend/super-admin-flow.md} §9). */
export default async function NewOrganizationAdminPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <PlatformShell
      title="Create administrator"
      description="Invite an administrator. Optionally designate them primary."
      permission="PLATFORM_ADMIN_CREATE"
    >
      <CreateAdminForm organizationId={id} />
    </PlatformShell>
  );
}
