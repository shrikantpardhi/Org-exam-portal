import { AdminShell } from "@/features/administration/components/admin-shell";
import { EditUserForm } from "@/features/administration/components/edit-user-form";

/** Edit a user's profile and roles ({@code docs/frontend/admin-flow.md} §7). */
export default async function EditUserPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <AdminShell
      title="Edit user"
      description="Change permitted profile fields and role assignments. Email is immutable."
      permission="USER_UPDATE"
    >
      <EditUserForm userId={id} />
    </AdminShell>
  );
}
