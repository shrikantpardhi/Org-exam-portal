import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

vi.mock("@/lib/auth/auth-context", () => ({
  useAuth: () => ({ can: () => true, status: "authenticated", permissions: [] }),
}));

import { apiGet, apiPost, apiPut } from "@/lib/api-client";
import { AnnouncementEditScreen } from "@/features/communication/components/announcement-edit-screen";
import { StudentAnnouncements } from "@/features/communication/components/student-announcements";

const mockedGet = vi.mocked(apiGet);
const mockedPost = vi.mocked(apiPost);
const mockedPut = vi.mocked(apiPut);

const draft = {
  id: "a1",
  title: "Maintenance window",
  description: "The platform will be unavailable tonight.",
  startDate: "2026-10-22T20:00:00Z",
  endDate: "2026-10-22T22:00:00Z",
  status: "INACTIVE",
};

const published = {
  ...draft,
  id: "a2",
  title: "New assessment schedule",
  description: "The new schedule is available.",
  status: "ACTIVE",
};

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedGet.mockImplementation((url: string) => {
    if (url === "/announcements/a1") {
      return Promise.resolve({ message: "Announcement retrieved", data: draft } as never);
    }
    if (url === "/announcements") {
      return Promise.resolve({ message: "Announcements retrieved", data: [published] } as never);
    }
    return Promise.reject(new Error(`Unexpected GET ${url}`));
  });
  mockedPut.mockResolvedValue({
    message: "Announcement updated",
    data: { ...draft, title: "Updated maintenance window" },
  } as never);
  mockedPost.mockResolvedValue({
    message: "Announcement published",
    data: { announcementId: "a1", status: "ACTIVE", fanoutJobId: "fanout-job-1" },
  } as never);
});

describe("announcement administration", () => {
  it("edits and publishes a draft announcement", async () => {
    const user = userEvent.setup();
    renderWithClient(<AnnouncementEditScreen announcementId="a1" />);

    const title = await screen.findByLabelText("Title");
    await user.clear(title);
    await user.type(title, "Updated maintenance window");
    await user.click(screen.getByRole("button", { name: "Save announcement" }));

    await waitFor(() => {
      expect(mockedPut).toHaveBeenCalledWith(
        "/announcements/a1",
        expect.objectContaining({ title: "Updated maintenance window" }),
      );
    });

    await user.click(screen.getByRole("button", { name: "Publish announcement" }));

    expect(await screen.findByText(/Announcement published/)).toBeInTheDocument();
    expect(mockedPost).toHaveBeenCalledWith("/announcements/a1/publish");
  });
});

describe("student announcements", () => {
  it("shows only the published announcement content and no admin controls", async () => {
    renderWithClient(<StudentAnnouncements />);

    const article = await screen.findByRole("article", {
      name: "New assessment schedule",
    });
    expect(article).toHaveTextContent("The new schedule is available.");
    expect(article).toHaveTextContent("Visible from");
    expect(screen.queryByRole("link", { name: "Edit" })).toBeNull();
    expect(screen.queryByRole("button", { name: /Publish/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /Archive/ })).toBeNull();
  });

  it("shows an empty state when nothing is visible", async () => {
    mockedGet.mockResolvedValue({ message: "Announcements retrieved", data: [] } as never);
    renderWithClient(<StudentAnnouncements />);

    expect(await screen.findByText("No announcements right now.")).toBeInTheDocument();
  });
});
