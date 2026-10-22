"use client";

import { AdminShell } from "@/features/administration/components/admin-shell";
import { BusinessRulesScreen } from "@/features/administration/components/business-rules-screen";

/** Business rules ({@code specs/20-P11-Administration-V1.5.md}). */
export default function AdminPage() {
  return (
    <AdminShell
      title="Business rules"
      description="Configurable business behaviour with audited changes."
      permission="BUSINESS_RULE_VIEW"
    >
      <BusinessRulesScreen />
    </AdminShell>
  );
}
