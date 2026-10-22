"use client";

import { AdminShell } from "@/features/administration/components/admin-shell";
import { StudentsScreen } from "@/features/administration/components/students-screen";

/** Students ({@code specs/20-P11-Administration-V1.5.md}). */
export default function AdminPage() {
  return (
    <AdminShell
      title="Students"
      description="Users holding the STUDENT role, composed from the frozen APIs."
      permission="USER_VIEW"
    >
      <StudentsScreen />
    </AdminShell>
  );
}
