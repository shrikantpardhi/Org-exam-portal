import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { apiGet } from "@/lib/api-client";
import { AuthProvider } from "@/lib/auth/auth-context";
import { clearAccessToken, setAccessToken } from "@/lib/auth/token-storage";
import { createQueryClient } from "@/lib/query-client";

const mockedGet = vi.mocked(apiGet);

function renderGuard(permission?: string) {
  const queryClient = createQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <AuthenticatedGuard permission={permission}>
          <p>Protected content</p>
        </AuthenticatedGuard>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe("AuthenticatedGuard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearAccessToken();
  });

  it("denies access when unauthenticated", () => {
    renderGuard();

    expect(screen.getByRole("alert")).toHaveTextContent("You must sign in to view this page.");
    expect(screen.queryByText("Protected content")).toBeNull();
  });

  it("renders content for an authenticated user with the permission", async () => {
    setAccessToken("jwt");
    mockedGet.mockResolvedValue({
      message: "Current user retrieved",
      data: {
        id: "u1",
        firstName: "Ada",
        lastName: "Lovelace",
        email: "ada@example.com",
        roles: ["ADMIN"],
        permissions: ["USER_VIEW"],
      },
    });
    renderGuard("USER_VIEW");

    expect(await screen.findByText("Protected content")).toBeInTheDocument();
  });

  it("denies content when the permission is missing", async () => {
    setAccessToken("jwt");
    mockedGet.mockResolvedValue({
      message: "Current user retrieved",
      data: {
        id: "u1",
        firstName: "Ada",
        lastName: "Lovelace",
        email: "ada@example.com",
        roles: ["STUDENT"],
        permissions: [],
      },
    });
    renderGuard("USER_VIEW");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You do not have access to this page.",
    );
    expect(screen.queryByText("Protected content")).toBeNull();
  });
});
