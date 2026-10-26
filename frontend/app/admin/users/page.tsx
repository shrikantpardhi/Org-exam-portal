"use client";

import Link from "next/link";

import { AdminShell } from "@/features/administration/components/admin-shell";
import { UsersScreen } from "@/features/administration/components/users-screen";
import { useAuth } from "@/lib/auth/auth-context";

/** Users ({@code specs/20-P11-Administration-V1.5.md}). */
export default function AdminPage() {
  const { can } = useAuth();
  return (
    <AdminShell
      title="Users"
      description="Paginated user administration with permission-gated actions."
      permission="USER_VIEW"
      actions={
        can("USER_CREATE") ? (
          <Link
            href="/admin/users/new"
            className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
          >
            + New user
          </Link>
        ) : undefined
      }
    >
      <UsersScreen />
    </AdminShell>
  );
}
