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

vi.mock("next/navigation", () => ({
  usePathname: () => "/admin/users",
}));

import { apiDelete, apiGet } from "@/lib/api-client";
import { AdminNav } from "@/features/administration/components/admin-nav";
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
    return Promise.reject(new Error(`Unexpected GET ${url}`));
  });
  mockedDelete.mockResolvedValue(undefined as never);
});

describe("permission-gated actions", () => {
  it("hides the archive action without USER_ARCHIVE", async () => {
    renderWithClient(<UsersScreen />);

    expect(await screen.findByText("ada@example.com")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Archive" })).toBeNull();
    expect(screen.getAllByText("View only").length).toBeGreaterThan(0);
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

describe("permission-driven navigation", () => {
  it("shows only the sections the caller may use", () => {
    auth.permissions = ["USER_VIEW"];
    render(<AdminNav />);

    expect(screen.getByRole("link", { name: "Users" })).toHaveAttribute("href", "/admin/users");
    expect(screen.getByRole("link", { name: "Students" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Audit" })).toBeNull();
    expect(screen.queryByRole("link", { name: "Settings" })).toBeNull();
  });

  it("shows every configured route for a full admin permission set", () => {
    auth.permissions = [
      "ADMIN_DASHBOARD_VIEW",
      "USER_VIEW",
      "ROLE_VIEW",
      "PERMISSION_VIEW",
      "ORGANIZATION_SETTING_VIEW",
      "BUSINESS_RULE_VIEW",
      "LOOKUP_VIEW",
      "AUDIT_LOG_VIEW",
      "SYSTEM_HEALTH_VIEW",
      "ACCESS_PACKAGE_MANAGE",
      "COMMUNICATION_TEMPLATE_READ",
    ];
    render(<AdminNav />);

    expect(screen.getByRole("link", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Audit" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "System health" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Commerce" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Communication" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Users" })).toHaveAttribute("aria-current", "page");
  });
});
