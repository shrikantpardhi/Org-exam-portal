"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { AnnouncementListScreen } from "@/features/communication/components/announcement-list-screen";

/** Announcement administration ({@code specs/19-P10-Communication-V1.5.md §109}). */
export default function AdminAnnouncementsPage() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-slate-500">Administration · Communication</p>
        <h1 className="text-2xl font-semibold tracking-tight">Announcements</h1>
      </header>
      <AuthenticatedGuard permission="ANNOUNCEMENT_READ">
        <AnnouncementListScreen />
      </AuthenticatedGuard>
    </main>
  );
}
