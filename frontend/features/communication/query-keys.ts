/**
 * Communication query keys ({@code specs/19-P10-Communication-V1.5.md §105, §106}).
 *
 * <p>Notification lists are keyed by their status filter so a mark-read mutation can update every
 * cached list and the derived unread count without touching unrelated server state.
 */
export type NotificationFilter = "ALL" | "UNREAD";

export const communicationKeys = {
  notifications: () => ["communication", "notifications"] as const,
  notificationList: (filter: NotificationFilter) =>
    ["communication", "notifications", "list", filter] as const,
  unread: () => ["communication", "notifications", "unread"] as const,
  templates: (status?: string, search?: string) =>
    ["communication", "templates", status ?? "ALL", search ?? ""] as const,
  template: (templateId: string) => ["communication", "template", templateId] as const,
  announcements: (status?: string) => ["communication", "announcements", status ?? "ALL"] as const,
  announcement: (announcementId: string) =>
    ["communication", "announcement", announcementId] as const,
};
