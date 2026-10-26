"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { NotificationCenter } from "@/features/communication/components/notification-center";
import { NotificationInbox } from "@/features/communication/components/notification-inbox";

/** The student notification center ({@code specs/19-P10-Communication-V1.5.md §104-§107}). */
export default function StudentNotificationsPage() {
  return (
    <div className="flex w-full max-w-3xl flex-col gap-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <p className="text-sm text-muted-foreground">Student</p>
          <h1 className="text-2xl font-semibold tracking-tight">Notifications</h1>
          <p className="text-sm text-muted-foreground">
            Your in-app notifications. Marking read is optimistic and rolls back if the server
            rejects the change.
          </p>
        </div>
        <AuthenticatedGuard>
          <NotificationCenter />
        </AuthenticatedGuard>
      </header>
      <AuthenticatedGuard>
        <NotificationInbox />
      </AuthenticatedGuard>
    </div>
  );
}
