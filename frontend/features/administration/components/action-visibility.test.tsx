import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
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

const auth = vi.hoisted(() => ({ permissions: ["USER_VIEW"] as string[] }));

vi.mock("@/lib/auth/auth-context", () => ({
  useAuth: () => ({
    can: (permission: string) => auth.permissions.includes(permission),
    status: "authenticated",
    permissions: auth.permissions,
  }),
}));

import { apiDelete, apiGet } from "@/lib/api-client";
import { UsersScreen } from "@/features/administration/components/users-screen";

const mockedGet = vi.mocked(apiGet);
const mockedDelete = vi.mocked(apiDelete);

const user = {
  id: "u1",
  firstName: "Ada",
  lastName: "Lovelace",
  email: "ada@example.com",
  mobile: null,
  status: "ACTIVE",
  roles: ["STUDENT"],
};

const role = { id: "role1", code: "STUDENT", name: "Student", description: null, status: "ACTIVE" };

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  vi.clearAllMocks();
  auth.permissions = ["USER_VIEW"];
  mockedGet.mockImplementation((url: string) => {
    if (url === "/users") {
      return Promise.resolve({
        message: "Users retrieved",
        data: [user],
        pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      } as never);
    }
    if (url === "/roles") {
      return Promise.resolve({
        message: "Roles retrieved",
        data: [role],
        pagination: { page: 0, size: 100, totalElements: 1, totalPages: 1 },
      } as never);
    }
    return Promise.reject(new Error("Unexpected GET " + url));
  });
  mockedDelete.mockResolvedValue(undefined as never);
});

describe("permission-gated actions", () => {
  it("hides the archive action without USER_ARCHIVE", async () => {
    renderWithClient(<UsersScreen />);

    expect(await screen.findByText("ada@example.com")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Archive" })).toBeNull();
    expect(screen.getByRole("link", { name: "View" })).toHaveAttribute("href", "/admin/users/u1");
  });

  it("shows the archive action with USER_ARCHIVE and calls the frozen endpoint", async () => {
    auth.permissions = ["USER_VIEW", "USER_ARCHIVE", "ROLE_VIEW"];
    const userEventSetup = userEvent.setup();
    renderWithClient(<UsersScreen />);

    const archiveButton = await screen.findByRole("button", { name: "Archive" });
    await userEventSetup.click(archiveButton);

    const dialog = await screen.findByRole("dialog", { name: "Archive user" });
    expect(dialog).toHaveTextContent("Ada Lovelace");

    await userEventSetup.click(screen.getByRole("button", { name: "Archive user" }));

    expect(mockedDelete).toHaveBeenCalledWith("/users/u1");
  });
});
