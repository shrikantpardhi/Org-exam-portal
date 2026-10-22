import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

import { apiGet, apiPost } from "@/lib/api-client";
import { NotificationCenter } from "@/features/communication/components/notification-center";

const mockedGet = vi.mocked(apiGet);
const mockedPost = vi.mocked(apiPost);

const notification = {
  id: "n1",
  title: "Result published",
  message: "Your result is available.",
  notificationStatus: "UNREAD",
  createdOn: "2026-10-22T10:00:00Z",
};

let serverUnread = true;

function renderCenter() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <NotificationCenter />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  serverUnread = true;
  mockedGet.mockImplementation((_url, params) => {
    const isCount = (params as { size?: number } | undefined)?.size === 1;
    const total = serverUnread ? 1 : 0;
    return Promise.resolve({
      message: "Notifications retrieved",
      data: isCount
        ? serverUnread
          ? [notification]
          : []
        : [{ ...notification, notificationStatus: serverUnread ? "UNREAD" : "READ" }],
      pagination: { page: 0, size: isCount ? 1 : 20, totalElements: total, totalPages: 1 },
    } as never);
  });
});

describe("notification center", () => {
  it("shows the unread badge and marks one notification read optimistically", async () => {
    const user = userEvent.setup();
    mockedPost.mockResolvedValue({ message: "Notification marked read", data: null } as never);

    renderCenter();

    expect(await screen.findByLabelText("1 unread notifications")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Notifications" }));
    expect(screen.getByRole("region", { name: "Notifications" })).toBeInTheDocument();
    expect(screen.getByText("Result published")).toBeInTheDocument();

    serverUnread = false;
    await user.click(
      screen.getByRole("button", { name: 'Mark "Result published" as read' }),
    );

    expect((await screen.findAllByText("Read")).length).toBeGreaterThan(0);
    expect(screen.queryByLabelText("1 unread notifications")).toBeNull();
    expect(mockedPost).toHaveBeenCalledWith("/notifications/n1/read");
  });

  it("marks all notifications read", async () => {
    const user = userEvent.setup();
    mockedPost.mockResolvedValue({ message: "Notifications marked read", data: 1 } as never);

    renderCenter();
    await screen.findByLabelText("1 unread notifications");

    await user.click(screen.getByRole("button", { name: "Notifications" }));
    serverUnread = false;
    await user.click(screen.getByRole("button", { name: /Mark all read/ }));

    expect(await screen.findByText("Read")).toBeInTheDocument();
    expect(mockedPost).toHaveBeenCalledWith("/notifications/read-all");
  });

  it("filters by unread through the frozen query parameter", async () => {
    const user = userEvent.setup();
    renderCenter();
    await screen.findByLabelText("1 unread notifications");

    await user.click(screen.getByRole("button", { name: "Notifications" }));
    await user.click(screen.getByRole("button", { name: "Unread" }));

    const unreadCall = mockedGet.mock.calls.find(
      (call) => (call[1] as { status?: string } | undefined)?.status === "UNREAD",
    );
    expect(unreadCall).toBeDefined();
  });
});
