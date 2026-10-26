"use client";

import { useParams } from "next/navigation";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { AnnouncementEditScreen } from "@/features/communication/components/announcement-edit-screen";

/** Announcement edit and publish ({@code specs/19-P10-Communication-V1.5.md §109}). */
export default function AdminAnnouncementEditPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Administration · Communication</p>
        <h1 className="text-2xl font-semibold tracking-tight">Edit announcement</h1>
      </header>
      <AuthenticatedGuard permission="ANNOUNCEMENT_READ">
        <AnnouncementEditScreen announcementId={id} />
      </AuthenticatedGuard>
    </div>
  );
}
