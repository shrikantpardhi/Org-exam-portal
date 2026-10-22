"use client";

/**
 * Schedule authoring route ({@code specs/13-P4-Assessment-V1.5.md §98, §109}).
 */
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { ScheduleList } from "@/features/assessment/components/schedule-list";

export default function SchedulesPage() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Schedules</h1>
        <p className="text-sm text-slate-500">
          Windows are authored in your local time and sent to the server as ISO-8601 instants. The
          backend validates the final values.
        </p>
      </header>
      <AuthenticatedGuard permission="ASSESSMENT_SCHEDULE_VIEW">
        <ScheduleList />
      </AuthenticatedGuard>
    </main>
  );
}
