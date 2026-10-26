import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/platform",
}));

const mockUseAuth = vi.fn();
vi.mock("@/lib/auth/auth-context", () => ({
  useAuth: () => mockUseAuth(),
}));

vi.mock("@/features/communication/hooks/use-communication", () => ({
  useNotifications: () => ({ data: { items: [], total: 0 } }),
  useUnreadCount: () => ({ data: 0 }),
}));

import { AppShell } from "@/components/shell/app-shell";
import { createQueryClient } from "@/lib/query-client";

function renderShell() {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <AppShell role="SUPER_ADMIN">content</AppShell>
    </QueryClientProvider>,
  );
}

describe("AppShell gate", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("redirects unauthenticated users to /login preserving the intended URL", () => {
    mockUseAuth.mockReturnValue({ status: "unauthenticated", user: null, permissions: [], can: () => false });
    renderShell();
    expect(replace).toHaveBeenCalledWith("/login?next=%2Fplatform");
  });

  it("redirects must-change-password users to /change-password", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      user: { id: "u1", roles: ["SUPER_ADMIN"], mustChangePassword: true, firstName: "A", lastName: "B" },
      permissions: [],
      can: () => true,
      logout: vi.fn(),
    });
    renderShell();
    expect(replace).toHaveBeenCalledWith("/change-password");
  });

  it("redirects a wrong-role user to their own role home", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      user: { id: "u1", roles: ["ADMIN"], mustChangePassword: false, firstName: "A", lastName: "B" },
      permissions: [],
      can: () => true,
      logout: vi.fn(),
    });
    renderShell();
    expect(replace).toHaveBeenCalledWith("/admin");
  });

  it("renders the sidebar and content for an authorized user", async () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      user: {
        id: "u1",
        roles: ["SUPER_ADMIN"],
        mustChangePassword: false,
        firstName: "Ada",
        lastName: "Lovelace",
      },
      permissions: ["PLATFORM_ORGANIZATION_VIEW", "PLATFORM_PROVIDER_VIEW", "PLATFORM_SYSTEM_VIEW"],
      can: () => true,
      logout: vi.fn(),
    });
    renderShell();
    expect(await screen.findByText("content")).toBeInTheDocument();
    expect(screen.getByText("Dashboard")).toBeInTheDocument();
    expect(screen.getByText("Organizations")).toBeInTheDocument();
    expect(screen.getAllByText("Platform").length).toBeGreaterThan(0);
    expect(replace).not.toHaveBeenCalled();
  });
});
