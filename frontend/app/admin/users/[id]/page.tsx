import { AdminShell } from "@/features/administration/components/admin-shell";
import { UserDetailScreen } from "@/features/administration/components/user-detail-screen";

/** Read a single user ({@code docs/frontend/admin-flow.md} §6). */
export default async function UserDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <AdminShell
      title="User"
      description="User profile, roles and status."
      permission="USER_VIEW"
    >
      <UserDetailScreen userId={id} />
    </AdminShell>
  );
}
