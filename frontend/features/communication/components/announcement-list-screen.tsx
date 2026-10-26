"use client";

import { useState } from "react";
import Link from "next/link";

import { CommunicationForm } from "@/components/communication/communication-form";
import {
  CommunicationEmpty,
  CommunicationError,
  CommunicationLoading,
} from "@/components/communication/communication-states";
import {
  announcementStatusLabel,
  describeCommunicationError,
  formatDateTime,
} from "@/components/communication/format";
import { announcementFormSchema } from "@/features/communication/schemas/communication";
import {
  useAnnouncements,
  useCreateAnnouncement,
} from "@/features/communication/hooks/use-communication";
import { useAuth } from "@/lib/auth/auth-context";

/** Announcement administration ({@code specs/19-P10-Communication-V1.5.md §109}). */
export function AnnouncementListScreen() {
  const { can } = useAuth();
  const [status, setStatus] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const query = useAnnouncements(status || undefined);
  const create = useCreateAnnouncement();

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

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end gap-2">
        <div className="space-y-1">
          <label htmlFor="announcement-status" className="text-sm font-medium">
            Status
          </label>
          <select
            id="announcement-status"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
            className="rounded-md border border-input px-3 py-2 text-sm"
          >
            <option value="">All</option>
            <option value="INACTIVE">Draft</option>
            <option value="ACTIVE">Published</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </div>
      </div>

      <section aria-labelledby="announcement-list-heading" className="space-y-3">
        <h2 id="announcement-list-heading" className="text-lg font-semibold">
          Announcements
        </h2>
        {announcements.length === 0 ? (
          <CommunicationEmpty message="No announcements match this view." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Announcements</caption>
              <thead>
                <tr className="border-b border-border">
                  <th scope="col" className="py-2 pr-3">Title</th>
                  <th scope="col" className="py-2 pr-3">Status</th>
                  <th scope="col" className="py-2 pr-3">Visible from</th>
                  <th scope="col" className="py-2 pr-3">Visible until</th>
                  <th scope="col" className="py-2">Actions</th>
                </tr>
              </thead>
              <tbody>
                {announcements.map((announcement) => (
                  <tr key={announcement.id} className="border-b border-border">
                    <th scope="row" className="py-2 pr-3 font-normal">{announcement.title}</th>
                    <td className="py-2 pr-3">{announcementStatusLabel(announcement.status)}</td>
                    <td className="py-2 pr-3">{formatDateTime(announcement.startDate) ?? "Immediately"}</td>
                    <td className="py-2 pr-3">{formatDateTime(announcement.endDate) ?? "No end date"}</td>
                    <td className="py-2">
                      <Link
                        href={`/admin/communication/announcements/${announcement.id}`}
                        className="rounded font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                      >
                        Edit
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {can("ANNOUNCEMENT_WRITE") ? (
        <section aria-labelledby="create-announcement-heading" className="space-y-3">
          <h2 id="create-announcement-heading" className="text-lg font-semibold">
            Create a draft announcement
          </h2>
          <CommunicationForm
            schema={announcementFormSchema}
            submitLabel="Create announcement"
            resetOnSuccess
            fields={[
              { name: "title", label: "Title" },
              { name: "description", label: "Description", type: "textarea" },
              { name: "startDate", label: "Visible from", type: "datetime-local" },
              { name: "endDate", label: "Visible until", type: "datetime-local" },
            ]}
            onSubmit={async (values) => {
              await create.mutateAsync({ ...values });
              setMessage("Draft announcement created.");
            }}
          />
          {message ? (
            <p role="status" className="text-sm text-emerald-700">
              {message}
            </p>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}
