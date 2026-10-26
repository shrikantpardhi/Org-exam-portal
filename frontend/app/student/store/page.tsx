"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { StoreCatalog } from "@/features/commerce/components/store-catalog";

/** Student store ({@code specs/18-P9-Commerce-V1.5.md §77}). */
export default function StudentStorePage() {
  return (
    <div className="flex w-full max-w-5xl flex-col gap-6">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Student</p>
        <h1 className="text-2xl font-semibold tracking-tight">Explore assessments</h1>
        <p className="text-sm text-muted-foreground">
          Browse access packages. Prices and durations come from the server; buying never unlocks
          content on the client.
        </p>
      </header>
      <AuthenticatedGuard>
        <StoreCatalog />
      </AuthenticatedGuard>
    </div>
  );
}
