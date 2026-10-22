"use client";

import { AdminPageHeader } from "@/components/admin/admin-page-header";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { AdminNav } from "@/features/administration/components/admin-nav";

/** The administration entry point ({@code specs/20-P11-Administration-V1.5.md §63}). */
export default function AdminHomePage() {
  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-6 py-12">
      <AdminPageHeader
        title="Administration"
        description="Permission-driven administration sections. Hidden links are not authorization; the backend enforces every permission."
      />
      <AuthenticatedGuard>
        <AdminNav />
      </AuthenticatedGuard>
    </main>
  );
}
