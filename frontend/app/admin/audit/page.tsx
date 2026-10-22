"use client";

import { AdminShell } from "@/features/administration/components/admin-shell";
import { AuditScreen } from "@/features/administration/components/audit-screen";

/** Audit ({@code specs/20-P11-Administration-V1.5.md}). */
export default function AdminPage() {
  return (
    <AdminShell
      title="Audit"
      description="Search the immutable audit trail. Read-only."
      permission="AUDIT_LOG_VIEW"
    >
      <AuditScreen />
    </AdminShell>
  );
}
