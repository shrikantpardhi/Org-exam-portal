"use client";

import { AdminShell } from "@/features/administration/components/admin-shell";
import { LookupsScreen } from "@/features/administration/components/lookups-screen";

/** Lookups ({@code specs/20-P11-Administration-V1.5.md}). */
export default function AdminPage() {
  return (
    <AdminShell
      title="Lookups"
      description="Reusable configurable master values."
      permission="LOOKUP_VIEW"
    >
      <LookupsScreen />
    </AdminShell>
  );
}
