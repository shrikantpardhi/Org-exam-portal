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

const auth = vi.hoisted(() => ({
  permissions: ["ROLE_VIEW", "ROLE_PERMISSION_VIEW", "ROLE_PERMISSION_ASSIGN"] as string[],
  roles: ["ADMIN"] as string[],
}));

vi.mock("@/lib/auth/auth-context", () => ({
  useAuth: () => ({
    can: (permission: string) => auth.permissions.includes(permission),
    status: "authenticated",
    permissions: auth.permissions,
    user: { roles: auth.roles },
  }),
}));

import { apiGet } from "@/lib/api-client";
import { RolesScreen } from "@/features/administration/components/roles-screen";

const mockedGet = vi.mocked(apiGet);

const roles = [
  { id: "r-admin", code: "ADMIN", name: "Administrator", description: null, status: "ACTIVE" },
  { id: "r-student", code: "STUDENT", name: "Student", description: null, status: "ACTIVE" },
  { id: "r-super", code: "SUPER_ADMIN", name: "Super admin", description: null, status: "ACTIVE" },
];

const permissions = [
  { id: "p1", code: "ROLE_VIEW", name: "View roles", description: null, status: "ACTIVE" },
];

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  vi.clearAllMocks();
  auth.permissions = ["ROLE_VIEW", "ROLE_PERMISSION_VIEW", "ROLE_PERMISSION_ASSIGN"];
  auth.roles = ["ADMIN"];
  mockedGet.mockImplementation((url: string) => {
    if (url === "/roles") {
      return Promise.resolve({
        message: "Roles retrieved",
        data: roles,
        pagination: { page: 0, size: 20, totalElements: 3, totalPages: 1 },
      } as never);
    }
    if (url === "/permissions") {
      return Promise.resolve({
        message: "Permissions retrieved",
        data: permissions,
        pagination: { page: 0, size: 100, totalElements: 1, totalPages: 1 },
      } as never);
    }
    if (url.startsWith("/roles/")) {
      return Promise.resolve({ message: "Role permissions retrieved", data: [] } as never);
    }
    return Promise.resolve({ message: "ok", data: [] } as never);
  });
});

/**
 * The roles screen must not invite privilege escalation.
 *
 * <p>SUPER_ADMIN is a platform role and never belongs to an organization, so listing it here would
 * offer an administrator a row they cannot act on. And an administrator opening one of their own
 * roles is editing their own access, which the screen must render read-only even when they hold
 * ROLE_PERMISSION_ASSIGN.
 */
describe("RolesScreen organization-scope guards", () => {
  it("does not list the platform SUPER_ADMIN role", async () => {
    renderWithClient(<RolesScreen />);
    await waitFor(() => {
      expect(screen.getByText("Student")).toBeInTheDocument();
    });
    expect(screen.queryByText("Super admin")).not.toBeInTheDocument();
    expect(screen.queryByText("SUPER_ADMIN")).not.toBeInTheDocument();
  });

  it("offers Cancel next to Save once a role is opened", async () => {
    renderWithClient(<RolesScreen />);
    const buttons = await screen.findAllByRole("button", { name: /manage permissions/i });
    await userEvent.click(buttons[1] as HTMLElement);

    expect(await screen.findByRole("button", { name: /save permissions/i })).toBeInTheDocument();
    const cancel = screen.getByRole("button", { name: /^cancel$/i });
    expect(cancel).toBeInTheDocument();

    await userEvent.click(cancel);
    await waitFor(() => {
      expect(screen.queryByRole("button", { name: /save permissions/i })).not.toBeInTheDocument();
    });
  });

  it("renders the signed-in administrator's own role as read-only", async () => {
    renderWithClient(<RolesScreen />);
    const buttons = await screen.findAllByRole("button", { name: /manage permissions/i });
    await userEvent.click(buttons[0] as HTMLElement);

    expect(await screen.findByText(/one of your own roles/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /save permissions/i })).not.toBeInTheDocument();
  });
});
