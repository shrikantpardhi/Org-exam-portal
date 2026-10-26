"use client";

import Link from "next/link";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { useAuth } from "@/lib/auth/auth-context";

/** Communication administration entry point ({@code specs/19-P10-Communication-V1.5.md §104}). */
export default function AdminCommunicationPage() {
  const { can } = useAuth();

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Administration</p>
        <h1 className="text-2xl font-semibold tracking-tight">Communication</h1>
        <p className="text-sm text-muted-foreground">
          Email templates and organization announcements. Publication never streams synchronously;
          the fan-out is a durable background job.
        </p>
      </header>
      <AuthenticatedGuard>
        <nav aria-label="Communication administration" className="grid gap-4 sm:grid-cols-2">
          {can("COMMUNICATION_TEMPLATE_READ") ? (
            <Link
              href="/admin/communication/templates"
              className="rounded-lg border border-border bg-surface p-4 shadow-sm hover:border-info focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
            >
              <h2 className="text-base font-semibold">Email templates</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                List, edit, preview and archive the email content catalog.
              </p>
            </Link>
          ) : null}
          {can("ANNOUNCEMENT_READ") ? (
            <Link
              href="/admin/communication/announcements"
              className="rounded-lg border border-border bg-surface p-4 shadow-sm hover:border-info focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
            >
              <h2 className="text-base font-semibold">Announcements</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                Create drafts, edit them, publish and archive.
              </p>
            </Link>
          ) : null}
        </nav>
      </AuthenticatedGuard>
    </div>
  );
}
