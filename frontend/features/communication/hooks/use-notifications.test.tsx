import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

import { apiGet, apiPost } from "@/lib/api-client";
import { ApiClientError } from "@/lib/errors";
import {
  useMarkAllRead,
  useMarkRead,
  useNotifications,
  useUnreadCount,
} from "@/features/communication/hooks/use-communication";

const mockedGet = vi.mocked(apiGet);
const mockedPost = vi.mocked(apiPost);

const unreadNotification = {
  id: "n1",
  title: "Result published",
  message: "Your result is available.",
  notificationStatus: "UNREAD",
  createdOn: "2026-10-22T10:00:00Z",
};

let serverUnread = true;

function notificationPayload(status: string) {
  return {
    message: "Notifications retrieved",
    data: serverUnread
      ? [{ ...unreadNotification, notificationStatus: status }]
      : [],
    pagination: { page: 0, size: 20, totalElements: serverUnread ? 1 : 0, totalPages: 1 },
  };
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  serverUnread = true;
  mockedGet.mockImplementation((_url, params) => {
    const status = (params as { status?: string } | undefined)?.status;
    return Promise.resolve(notificationPayload(status === "UNREAD" ? "UNREAD" : "UNREAD") as never);
  });
});

describe("optimistic mark-read", () => {
  it("removes the notification immediately and rolls back when the mutation fails", async () => {
    let reject!: (reason?: unknown) => void;
    mockedPost.mockReturnValue(
      new Promise<never>((_resolve, rejectPromise) => {
        reject = rejectPromise;
      }) as never,
    );

    const { result } = renderHook(
      () => ({
        list: useNotifications("UNREAD"),
        unread: useUnreadCount(),
        markRead: useMarkRead(),
      }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.list.data?.items).toHaveLength(1));
    await waitFor(() => expect(result.current.unread.data).toBe(1));

    act(() => {
      result.current.markRead.mutate("n1");
    });

    await waitFor(() => expect(result.current.list.data?.items).toHaveLength(0));
    await waitFor(() => expect(result.current.unread.data).toBe(0));

    await act(async () => {
      reject(new ApiClientError({ message: "Failed", status: 500, code: "SERVER_ERROR" }));
    });

    await waitFor(() => expect(result.current.list.data?.items).toHaveLength(1));
    expect(result.current.list.data?.items[0]?.notificationStatus).toBe("UNREAD");
    await waitFor(() => expect(result.current.unread.data).toBe(1));
  });

  it("keeps the optimistic state on success", async () => {
    mockedPost.mockResolvedValue({ message: "Notification marked read", data: null } as never);

    const { result } = renderHook(
      () => ({
        list: useNotifications("UNREAD"),
        unread: useUnreadCount(),
        markRead: useMarkRead(),
      }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.list.data?.items).toHaveLength(1));
    serverUnread = false;

    act(() => {
      result.current.markRead.mutate("n1");
    });

    await waitFor(() => expect(result.current.list.data?.items).toHaveLength(0));
    await waitFor(() => expect(result.current.unread.data).toBe(0));
    expect(mockedPost).toHaveBeenCalledWith("/notifications/n1/read");
  });
});

describe("optimistic mark-all-read", () => {
  it("clears the unread list and the unread count optimistically", async () => {
    mockedPost.mockResolvedValue({ message: "Notifications marked read", data: 1 } as never);

    const { result } = renderHook(
      () => ({
        list: useNotifications("UNREAD"),
        unread: useUnreadCount(),
        markAll: useMarkAllRead(),
      }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.list.data?.items).toHaveLength(1));
    serverUnread = false;

    act(() => {
      result.current.markAll.mutate();
    });

    await waitFor(() => expect(result.current.list.data?.items).toHaveLength(0));
    await waitFor(() => expect(result.current.unread.data).toBe(0));
    expect(mockedPost).toHaveBeenCalledWith("/notifications/read-all");
  });
});
