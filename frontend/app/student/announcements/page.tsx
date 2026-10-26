"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { NotificationCenter } from "@/features/communication/components/notification-center";
import { StudentAnnouncements } from "@/features/communication/components/student-announcements";

/** Student announcements ({@code specs/19-P10-Communication-V1.5.md §68, §108}). */
export default function StudentAnnouncementsPage() {
  return (
    <div className="flex w-full max-w-3xl flex-col gap-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <p className="text-sm text-muted-foreground">Student</p>
          <h1 className="text-2xl font-semibold tracking-tight">Announcements</h1>
          <p className="text-sm text-muted-foreground">
            Published announcements that are currently visible.
          </p>
        </div>
        <AuthenticatedGuard>
          <NotificationCenter />
        </AuthenticatedGuard>
      </header>
      <AuthenticatedGuard>
        <StudentAnnouncements />
      </AuthenticatedGuard>
    </div>
  );
}
