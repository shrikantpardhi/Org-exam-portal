"use client";

import { AdminShell } from "@/features/administration/components/admin-shell";
import { UsersScreen } from "@/features/administration/components/users-screen";

/** Users ({@code specs/20-P11-Administration-V1.5.md}). */
export default function AdminPage() {
  return (
    <AdminShell
      title="Users"
      description="Paginated user administration with permission-gated actions."
      permission="USER_VIEW"
    >
      <UsersScreen />
    </AdminShell>
  );
}
