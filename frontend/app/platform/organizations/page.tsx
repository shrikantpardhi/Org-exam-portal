import Link from "next/link";

import { OrganizationsScreen } from "@/features/platform/components/organizations-screen";
import { PlatformShell } from "@/features/platform/components/platform-shell";

/** Business organizations list ({@code docs/frontend/super-admin-flow.md} §5). */
export default function OrganizationsPage() {
  return (
    <PlatformShell
      title="Organizations"
      description="Business organizations. The PLATFORM tenant is not listed."
      permission="PLATFORM_ORGANIZATION_VIEW"
      actions={
        <Link
          href="/platform/organizations/new"
          className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
        >
          + Create organization
        </Link>
      }
    >
      <OrganizationsScreen />
    </PlatformShell>
  );
}
