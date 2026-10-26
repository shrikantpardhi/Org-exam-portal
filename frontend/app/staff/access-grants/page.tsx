"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { AccessGrantAdmin } from "@/features/commerce/components/access-grant-admin";

/** Staff manual access grants ({@code specs/18-P9-Commerce-V1.5.md §88}). */
export default function StaffAccessGrantsPage() {
  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Staff</p>
        <h1 className="text-2xl font-semibold tracking-tight">Access grants</h1>
        <p className="text-sm text-muted-foreground">
          Manual grants are validated and audited by the server; the UI never merges grants on its
          own.
        </p>
      </header>
      <AuthenticatedGuard permission="ACCESS_GRANT_MANAGE">
        <AccessGrantAdmin />
      </AuthenticatedGuard>
    </div>
  );
}
