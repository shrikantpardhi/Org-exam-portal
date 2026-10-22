/**
 * Communication API layer ({@code specs/19-P10-Communication-V1.5.md §12, §42, §65-§68}).
 *
 * <p>Only frozen endpoints are called. Notifications are self-scoped by the backend; the client
 * never supplies a recipient. There is no public notification-creation endpoint.
 */
import { z } from "zod";

import { apiDelete, apiGet, apiPost, apiPut } from "@/lib/api-client";
import {
  announcementSchema,
  emailTemplateSchema,
  notificationSchema,
  publishAnnouncementSchema,
  type Announcement,
  type EmailTemplate,
  type NotificationPage,
  type PublishAnnouncement,
} from "@/features/communication/schemas/communication";

export async function listNotifications(
  status: "UNREAD" | undefined,
  page = 0,
  size = 20,
): Promise<NotificationPage> {
  const result = await apiGet<unknown>("/notifications", {
    ...(status ? { status } : {}),
    page,
    size,
  });
  const items = z.array(notificationSchema).parse(result.data);
  return { items, total: result.pagination?.totalElements ?? items.length };
}

export async function markNotificationRead(notificationId: string): Promise<void> {
  await apiPost<unknown>(`/notifications/${notificationId}/read`);
}

export async function markAllNotificationsRead(): Promise<number> {
  const result = await apiPost<unknown>("/notifications/read-all");
  return typeof result.data === "number" ? result.data : 0;
}

export async function listEmailTemplates(status?: string, search?: string): Promise<EmailTemplate[]> {
  const result = await apiGet<unknown>("/email-templates", {
    ...(status ? { status } : {}),
    ...(search ? { search } : {}),
  });
  return z.array(emailTemplateSchema).parse(result.data);
}

export async function getEmailTemplate(templateId: string): Promise<EmailTemplate> {
  const result = await apiGet<unknown>(`/email-templates/${templateId}`);
  return emailTemplateSchema.parse(result.data);
}

export async function createEmailTemplate(body: Record<string, unknown>): Promise<EmailTemplate> {
  const result = await apiPost<unknown>("/email-templates", body);
  return emailTemplateSchema.parse(result.data);
}

export async function updateEmailTemplate(
  templateId: string,
  body: Record<string, unknown>,
): Promise<EmailTemplate> {
  const result = await apiPut<unknown>(`/email-templates/${templateId}`, body);
  return emailTemplateSchema.parse(result.data);
}

export async function archiveEmailTemplate(templateId: string): Promise<void> {
  await apiDelete(`/email-templates/${templateId}`);
}

/** Students receive only the visible rows; staff with {@code ANNOUNCEMENT_READ} receive all. */
export async function listAnnouncements(status?: string): Promise<Announcement[]> {
  const result = await apiGet<unknown>("/announcements", {
    ...(status ? { status } : {}),
  });
  return z.array(announcementSchema).parse(result.data);
}

export async function getAnnouncement(announcementId: string): Promise<Announcement> {
  const result = await apiGet<unknown>(`/announcements/${announcementId}`);
  return announcementSchema.parse(result.data);
}

export async function createAnnouncement(body: Record<string, unknown>): Promise<Announcement> {
  const result = await apiPost<unknown>("/announcements", body);
  return announcementSchema.parse(result.data);
}

export async function updateAnnouncement(
  announcementId: string,
  body: Record<string, unknown>,
): Promise<Announcement> {
  const result = await apiPut<unknown>(`/announcements/${announcementId}`, body);
  return announcementSchema.parse(result.data);
}

export async function publishAnnouncement(announcementId: string): Promise<PublishAnnouncement> {
  const result = await apiPost<unknown>(`/announcements/${announcementId}/publish`);
  return publishAnnouncementSchema.parse(result.data);
}

export async function archiveAnnouncement(announcementId: string): Promise<void> {
  await apiDelete(`/announcements/${announcementId}`);
}
