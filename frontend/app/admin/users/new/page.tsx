import { AdminShell } from "@/features/administration/components/admin-shell";
import { CreateUserForm } from "@/features/administration/components/create-user-form";

/** Create a non-privileged user ({@code docs/frontend/admin-flow.md} §5). */
export default function NewUserPage() {
  return (
    <AdminShell
      title="Create user"
      description="Create a STAFF / REVIEWER / SUPPORT / FINANCE / STUDENT account and send the invitation."
      permission="USER_CREATE"
    >
      <CreateUserForm />
    </AdminShell>
  );
}
