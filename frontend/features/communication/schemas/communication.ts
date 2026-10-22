/**
 * Communication response and form schemas ({@code specs/19-P10-Communication-V1.5.md §12, §42, §46, §75}).
 *
 * <p>Notification and announcement states are controlled strings. The frontend adds no creator,
 * audience or targeting field that the frozen contract does not expose. Template placeholders use
 * the frozen {@code {{variableName}}} syntax.
 */
import { z } from "zod";

const text = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

const timestamp = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

export const notificationStatusSchema = z
  .string()
  .nullish()
  .transform((value) => (value ?? "UNKNOWN").toUpperCase());

export const lifecycleStatusSchema = z
  .string()
  .nullish()
  .transform((value) => (value ?? "UNKNOWN").toUpperCase());

export const notificationSchema = z.object({
  id: z.string(),
  title: z.string(),
  message: text,
  notificationStatus: notificationStatusSchema,
  createdOn: timestamp,
});

export const emailTemplateSchema = z.object({
  id: z.string(),
  code: z.string(),
  name: z.string(),
  subject: text,
  body: text,
  status: lifecycleStatusSchema,
});

export const announcementSchema = z.object({
  id: z.string(),
  title: z.string(),
  description: text,
  startDate: timestamp,
  endDate: timestamp,
  status: lifecycleStatusSchema,
});

export const publishAnnouncementSchema = z.object({
  announcementId: z.string(),
  status: lifecycleStatusSchema,
  fanoutJobId: text,
});

export type NotificationStatus = z.infer<typeof notificationStatusSchema>;
export type Notification = z.infer<typeof notificationSchema>;
export type EmailTemplate = z.infer<typeof emailTemplateSchema>;
export type Announcement = z.infer<typeof announcementSchema>;
export type PublishAnnouncement = z.infer<typeof publishAnnouncementSchema>;

/** One page of the self-scoped notification list. */
export interface NotificationPage {
  items: Notification[];
  total: number;
}

// ------------------------------------------------------------------ form schemas

const optionalIsoDateTime = z
  .string()
  .trim()
  .optional()
  .transform((value) => (value ? new Date(value).toISOString() : null));

export const templateEditFormSchema = z.object({
  name: z.string().trim().min(1, "A name is required").max(150),
  subject: z.string().trim().max(255).optional(),
  body: z.string().optional(),
});

export const createTemplateFormSchema = z.object({
  code: z.string().trim().min(1, "A code is required").max(50),
  name: z.string().trim().min(1, "A name is required").max(150),
  subject: z.string().trim().max(255).optional(),
  body: z.string().optional(),
});

export const announcementFormSchema = z.object({
  title: z.string().trim().min(1, "A title is required").max(255),
  description: z.string().optional(),
  startDate: optionalIsoDateTime,
  endDate: optionalIsoDateTime,
});

export type TemplateEditForm = z.infer<typeof templateEditFormSchema>;
export type CreateTemplateForm = z.infer<typeof createTemplateFormSchema>;
export type AnnouncementForm = z.infer<typeof announcementFormSchema>;

/** The synthetic preview context ({@code §71}). Static data only; never real customer data. */
export const PREVIEW_CONTEXT: Record<string, string> = {
  studentName: "Sample Student",
  assessmentName: "SSC Mock Test 01",
  organizationName: "Demo Organization",
};

/** Replaces {@code {{variableName}}} placeholders; unknown placeholders are preserved ({@code §24}). */
export function renderTemplatePreview(template: string, model = PREVIEW_CONTEXT): string {
  return template.replace(/\{\{\s*([A-Za-z0-9_]+)\s*\}\}/g, (match, name: string) =>
    Object.prototype.hasOwnProperty.call(model, name) ? model[name]! : match,
  );
}
