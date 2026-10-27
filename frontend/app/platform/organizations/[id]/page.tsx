import { OrganizationDetailScreen } from "@/features/platform/components/organization-detail-screen";
import { PlatformShell } from "@/features/platform/components/platform-shell";

/** Business organization detail ({@code docs/frontend/super-admin-flow.md} §7-§8). */
export default async function OrganizationDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <PlatformShell
      title="Organization"
      permission="PLATFORM_ORGANIZATION_VIEW"
    >
      <OrganizationDetailScreen organizationId={id} />
    </PlatformShell>
  );
}
