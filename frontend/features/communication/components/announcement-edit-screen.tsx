"use client";

import Link from "next/link";
import { useState } from "react";

import { CommunicationForm } from "@/components/communication/communication-form";
import {
  CommunicationError,
  CommunicationLoading,
} from "@/components/communication/communication-states";
import {
  announcementStatusLabel,
  describeCommunicationError,
  shortId,
  toDateTimeLocal,
} from "@/components/communication/format";
import { announcementFormSchema } from "@/features/communication/schemas/communication";
import {
  useAnnouncement,
  useArchiveAnnouncement,
  usePublishAnnouncement,
  useUpdateAnnouncement,
} from "@/features/communication/hooks/use-communication";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * Announcement editing and publication ({@code specs/19-P10-Communication-V1.5.md §103, §109}).
 *
 * <p>Publishing is an explicit, permission-gated server action that returns once the announcement is
 * ACTIVE and a durable fan-out job exists; the UI never assumes the fan-out has completed.
 */
export function AnnouncementEditScreen({ announcementId }: { announcementId: string }) {
  const { can } = useAuth();
  const query = useAnnouncement(announcementId);
  const update = useUpdateAnnouncement(announcementId);
  const publish = usePublishAnnouncement(announcementId);
  const archive = useArchiveAnnouncement();
  const [message, setMessage] = useState<string | null>(null);

  if (announcementId.length === 0) {
    return <CommunicationError message="No announcement was selected." />;
  }

  if (query.isPending) {
    return <CommunicationLoading label="Loading announcement…" />;
  }

  if (query.isError) {
    return (
      <CommunicationError
        message={describeCommunicationError(query.error, "The announcement could not be loaded.")}
        onRetry={() => {
          void query.refetch();
        }}
      />
    );
  }

  const announcement = query.data;
  if (!announcement) {
    return <CommunicationLoading label="Loading announcement…" />;
  }

  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <p className="font-mono text-xs text-muted-foreground">{announcement.id}</p>
        <h2 className="text-xl font-semibold tracking-tight">{announcement.title}</h2>
        <p className="text-sm text-muted-foreground">
          Status: {announcementStatusLabel(announcement.status)}
        </p>
      </header>

      <nav aria-label="Announcement actions" className="flex flex-wrap gap-4 text-sm">
        <Link
          href="/admin/communication/announcements"
          className="rounded font-medium text-info underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          All announcements
        </Link>
      </nav>

      {!can("ANNOUNCEMENT_WRITE") ? (
        <p role="alert" className="text-sm text-destructive">
          You do not have permission to edit announcements.
        </p>
      ) : null}

      <CommunicationForm
        schema={announcementFormSchema}
        submitLabel="Save announcement"
        disabled={!can("ANNOUNCEMENT_WRITE")}
        defaultValues={{
          title: announcement.title,
          description: announcement.description ?? "",
          startDate: toDateTimeLocal(announcement.startDate),
          endDate: toDateTimeLocal(announcement.endDate),
        }}
        fields={[
          { name: "title", label: "Title" },
          { name: "description", label: "Description", type: "textarea" },
          { name: "startDate", label: "Visible from", type: "datetime-local" },
          { name: "endDate", label: "Visible until", type: "datetime-local" },
        ]}
        onSubmit={async (values) => {
          await update.mutateAsync({ ...values });
          setMessage("Announcement saved.");
        }}
      />

      {message ? (
        <p role="status" className="text-sm text-success">
          {message}
        </p>
      ) : null}
      {update.isError ? (
        <CommunicationError
          message={describeCommunicationError(update.error, "The announcement could not be saved.")}
        />
      ) : null}

      {can("ANNOUNCEMENT_PUBLISH") && announcement.status !== "ARCHIVED" ? (
        <div className="space-y-2 rounded-lg border border-border bg-surface p-4">
          <p className="text-sm text-muted-foreground">
            Publishing makes the announcement visible in its window and queues the notification
            fan-out. It does not send notifications synchronously.
          </p>
          {publish.isSuccess ? (
            <p role="status" className="text-sm text-success">
              Announcement published. Fan-out job:{" "}
              <code title={publish.data.fanoutJobId ?? undefined}>
                {shortId(publish.data.fanoutJobId)}
              </code>
            </p>
          ) : (
            <button
              type="button"
              onClick={() => {
                publish.mutate();
              }}
              disabled={publish.isPending}
              className="rounded bg-blue-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700"
            >
              {publish.isPending ? "Publishing…" : "Publish announcement"}
            </button>
          )}
          {publish.isError ? (
            <CommunicationError
              message={describeCommunicationError(
                publish.error,
                "The announcement could not be published.",
              )}
            />
          ) : null}
        </div>
      ) : null}

      {can("ANNOUNCEMENT_DELETE") && announcement.status !== "ARCHIVED" ? (
        <button
          type="button"
          onClick={() => {
            archive.mutate(announcement.id);
          }}
          className="rounded border border-destructive bg-surface px-4 py-2 text-sm font-medium text-destructive focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-700"
        >
          Archive announcement
        </button>
      ) : null}
      {archive.isError ? (
        <CommunicationError
          message={describeCommunicationError(
            archive.error,
            "The announcement could not be archived.",
          )}
        />
      ) : null}
    </div>
  );
}
