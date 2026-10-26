"use client";

import {
  CommunicationEmpty,
  CommunicationError,
  CommunicationLoading,
} from "@/components/communication/communication-states";
import { describeCommunicationError, formatDateTime } from "@/components/communication/format";
import { useAnnouncements } from "@/features/communication/hooks/use-communication";

/**
 * Student announcements ({@code specs/19-P10-Communication-V1.5.md §68, §108}).
 *
 * <p>Only title, description and the visibility window are shown. Creator and edit controls are
 * never rendered on the student surface.
 */
export function StudentAnnouncements() {
  const query = useAnnouncements();

  if (query.isPending) {
    return <CommunicationLoading label="Loading announcements…" />;
  }

  if (query.isError) {
    return (
      <CommunicationError
        message={describeCommunicationError(query.error, "Announcements could not be loaded.")}
        onRetry={() => {
          void query.refetch();
        }}
      />
    );
  }

  const announcements = query.data ?? [];

  if (announcements.length === 0) {
    return (
      <CommunicationEmpty
        message="No announcements right now."
        hint="Published announcements appear here while they are within their visibility window."
      />
    );
  }

  return (
    <section aria-label="Announcements" className="space-y-4">
      {announcements.map((announcement) => (
        <article
          key={announcement.id}
          aria-label={announcement.title}
          className="space-y-2 rounded-lg border border-border bg-surface p-4 shadow-sm"
        >
          <h2 className="text-base font-semibold text-foreground">{announcement.title}</h2>
          <p className="text-sm text-muted-foreground">
            {announcement.description ?? "No further details."}
          </p>
          <dl className="flex flex-wrap gap-4 text-xs text-muted-foreground">
            <div className="flex gap-1">
              <dt className="font-medium">Visible from</dt>
              <dd>
                {formatDateTime(announcement.startDate) ?? "Immediately"}
              </dd>
            </div>
            <div className="flex gap-1">
              <dt className="font-medium">Visible until</dt>
              <dd>{formatDateTime(announcement.endDate) ?? "No end date"}</dd>
            </div>
          </dl>
        </article>
      ))}
    </section>
  );
}
