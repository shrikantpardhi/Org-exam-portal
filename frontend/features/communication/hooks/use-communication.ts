"use client";

import { useMutation, useQuery, useQueryClient, type QueryKey } from "@tanstack/react-query";

import {
  archiveAnnouncement,
  archiveEmailTemplate,
  createAnnouncement,
  createEmailTemplate,
  getAnnouncement,
  getEmailTemplate,
  listAnnouncements,
  listEmailTemplates,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  publishAnnouncement,
  updateAnnouncement,
  updateEmailTemplate,
} from "@/features/communication/api/communication-api";
import {
  communicationKeys,
  type NotificationFilter,
} from "@/features/communication/query-keys";
import type { NotificationPage } from "@/features/communication/schemas/communication";
import type { ApiClientError } from "@/lib/errors";

// ------------------------------------------------------------------ notifications

export function useNotifications(filter: NotificationFilter) {
  return useQuery({
    queryKey: communicationKeys.notificationList(filter),
    queryFn: () => listNotifications(filter === "UNREAD" ? "UNREAD" : undefined),
  });
}

/**
 * The unread count derived from the paginated unread read ({@code §106}) — no dedicated count
 * endpoint is invented.
 */
export function useUnreadCount() {
  return useQuery({
    queryKey: communicationKeys.unread(),
    queryFn: async () => (await listNotifications("UNREAD", 0, 1)).total,
  });
}

interface ReadSnapshot {
  listSnapshots: [QueryKey, unknown][];
  unreadSnapshot: number | undefined;
}

function listQueries(queryClient: ReturnType<typeof useQueryClient>) {
  return queryClient
    .getQueryCache()
    .findAll({ queryKey: communicationKeys.notifications() })
    .filter((query) => query.queryKey[2] === "list");
}

function applyRead(page: NotificationPage, id: string, remove: boolean): NotificationPage {
  if (!page.items.some((notification) => notification.id === id)) {
    return page;
  }
  if (remove) {
    return {
      items: page.items.filter((notification) => notification.id !== id),
      total: Math.max(0, page.total - 1),
    };
  }
  return {
    items: page.items.map((notification) =>
      notification.id === id ? { ...notification, notificationStatus: "READ" } : notification,
    ),
    total: page.total,
  };
}

function applyAllRead(page: NotificationPage, empty: boolean): NotificationPage {
  if (empty) {
    return { items: [], total: 0 };
  }
  return {
    items: page.items.map((notification) => ({ ...notification, notificationStatus: "READ" })),
    total: page.total,
  };
}

/**
 * Marks one notification read with an optimistic update and a rollback on failure
 * ({@code specs/19-P10-Communication-V1.5.md §107}).
 *
 * <p>Every cached list is updated, and the unread count is decremented. A failure restores the
 * exact prior cache entries, so a rejected mutation never leaves a notification looking read.
 */
export function useMarkRead() {
  const queryClient = useQueryClient();
  return useMutation<void, ApiClientError, string, ReadSnapshot>({
    mutationFn: (notificationId) => markNotificationRead(notificationId),
    onMutate: async (notificationId) => {
      await queryClient.cancelQueries({ queryKey: communicationKeys.notifications() });
      const queries = listQueries(queryClient);
      const listSnapshots: [QueryKey, unknown][] = queries.map((query) => [
        query.queryKey,
        query.state.data,
      ]);
      const unreadSnapshot = queryClient.getQueryData<number>(communicationKeys.unread());

      for (const query of queries) {
        const filter = query.queryKey[3];
        queryClient.setQueryData<NotificationPage>(query.queryKey, (old) =>
          old ? applyRead(old, notificationId, filter === "UNREAD") : old,
        );
      }
      if (typeof unreadSnapshot === "number") {
        queryClient.setQueryData(communicationKeys.unread(), Math.max(0, unreadSnapshot - 1));
      }
      return { listSnapshots, unreadSnapshot };
    },
    onError: (_error, _notificationId, context) => {
      for (const [key, data] of context?.listSnapshots ?? []) {
        queryClient.setQueryData(key, data);
      }
      if (typeof context?.unreadSnapshot === "number") {
        queryClient.setQueryData(communicationKeys.unread(), context.unreadSnapshot);
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: communicationKeys.notifications() });
    },
  });
}

/** Marks every notification read with the same optimistic-then-rollback discipline. */
export function useMarkAllRead() {
  const queryClient = useQueryClient();
  return useMutation<number, ApiClientError, void, ReadSnapshot>({
    mutationFn: () => markAllNotificationsRead(),
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: communicationKeys.notifications() });
      const queries = listQueries(queryClient);
      const listSnapshots: [QueryKey, unknown][] = queries.map((query) => [
        query.queryKey,
        query.state.data,
      ]);
      const unreadSnapshot = queryClient.getQueryData<number>(communicationKeys.unread());

      for (const query of queries) {
        const filter = query.queryKey[3];
        queryClient.setQueryData<NotificationPage>(query.queryKey, (old) =>
          old ? applyAllRead(old, filter === "UNREAD") : old,
        );
      }
      if (typeof unreadSnapshot === "number") {
        queryClient.setQueryData(communicationKeys.unread(), 0);
      }
      return { listSnapshots, unreadSnapshot };
    },
    onError: (_error, _variables, context) => {
      for (const [key, data] of context?.listSnapshots ?? []) {
        queryClient.setQueryData(key, data);
      }
      if (typeof context?.unreadSnapshot === "number") {
        queryClient.setQueryData(communicationKeys.unread(), context.unreadSnapshot);
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: communicationKeys.notifications() });
    },
  });
}

// ------------------------------------------------------------------ email templates

export function useEmailTemplates(status?: string, search?: string) {
  return useQuery({
    queryKey: communicationKeys.templates(status, search),
    queryFn: () => listEmailTemplates(status, search),
  });
}

export function useEmailTemplate(templateId: string) {
  return useQuery({
    queryKey: communicationKeys.template(templateId),
    queryFn: () => getEmailTemplate(templateId),
    enabled: templateId.length > 0,
  });
}

export function useCreateEmailTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => createEmailTemplate(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["communication", "templates"] });
    },
  });
}

export function useUpdateEmailTemplate(templateId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => updateEmailTemplate(templateId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: communicationKeys.template(templateId) });
      void queryClient.invalidateQueries({ queryKey: ["communication", "templates"] });
    },
  });
}

export function useArchiveEmailTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (templateId: string) => archiveEmailTemplate(templateId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["communication", "templates"] });
    },
  });
}

// ------------------------------------------------------------------ announcements

export function useAnnouncements(status?: string) {
  return useQuery({
    queryKey: communicationKeys.announcements(status),
    queryFn: () => listAnnouncements(status),
  });
}

export function useAnnouncement(announcementId: string) {
  return useQuery({
    queryKey: communicationKeys.announcement(announcementId),
    queryFn: () => getAnnouncement(announcementId),
    enabled: announcementId.length > 0,
  });
}

export function useCreateAnnouncement() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => createAnnouncement(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["communication", "announcements"] });
    },
  });
}

export function useUpdateAnnouncement(announcementId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => updateAnnouncement(announcementId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: communicationKeys.announcement(announcementId),
      });
      void queryClient.invalidateQueries({ queryKey: ["communication", "announcements"] });
    },
  });
}

export function usePublishAnnouncement(announcementId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => publishAnnouncement(announcementId),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: communicationKeys.announcement(announcementId),
      });
      void queryClient.invalidateQueries({ queryKey: ["communication", "announcements"] });
    },
  });
}

export function useArchiveAnnouncement() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (announcementId: string) => archiveAnnouncement(announcementId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["communication", "announcements"] });
    },
  });
}
