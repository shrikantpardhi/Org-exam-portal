"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { AccessList } from "@/features/commerce/components/access-list";

/** My access ({@code specs/18-P9-Commerce-V1.5.md §82}). */
export default function StudentAccessPage() {
  return (
    <div className="flex w-full max-w-5xl flex-col gap-6">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Student</p>
        <h1 className="text-2xl font-semibold tracking-tight">My access</h1>
        <p className="text-sm text-muted-foreground">
          Entitlements are created by a confirmed payment or an authorized manual grant, and are
          enforced by the server.
        </p>
      </header>
      <AuthenticatedGuard>
        <AccessList />
      </AuthenticatedGuard>
    </div>
  );
}
