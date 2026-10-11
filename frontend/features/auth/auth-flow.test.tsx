import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Controlled end-to-end authentication flow ({@code specs/10-P1-Identity-V1.5.md §183, §184}).
 *
 * <p>Drives the real {@code AuthProvider}, {@code LoginForm}, and {@code AuthenticatedGuard}
 * against a stateful in-memory transport, proving:
 * login -> protected content -> logout -> protected content denied.
 * The backend contract for the same flow is proven by the backend integration tests.
 */
const fake = vi.hoisted(() => ({ authenticated: false }));

vi.mock("@/lib/api-client", () => ({
  apiPost: vi.fn(async (url: string) => {
    if (url === "/auth/login") {
      fake.authenticated = true;
      return {
        message: "Login successful",
        data: {
          accessToken: "jwt",
          tokenType: "Bearer",
          expiresIn: 3600,
          user: { id: "u1", firstName: "Ada", lastName: "Lovelace", roles: ["STUDENT"] },
        },
      };
    }
    if (url === "/auth/logout") {
      fake.authenticated = false;
      return { message: "Logout successful", data: undefined };
    }
    return { message: "ok", data: undefined };
  }),
  apiGet: vi.fn(async () => {
    if (!fake.authenticated) {
      throw new Error("unauthorized");
    }
    return {
      message: "Current user retrieved",
      data: {
        id: "u1",
        firstName: "Ada",
        lastName: "Lovelace",
        email: "ada@example.com",
        roles: ["STUDENT"],
        permissions: [],
      },
    };
  }),
  setUnauthorizedHandler: vi.fn(),
}));

import { LoginForm } from "@/features/auth/components/login-form";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { AuthProvider, useAuth } from "@/lib/auth/auth-context";
import { clearAccessToken } from "@/lib/auth/token-storage";
import { createQueryClient } from "@/lib/query-client";

function FlowHarness() {
  const { logout } = useAuth();
  return (
    <div>
      <LoginForm />
      <AuthenticatedGuard>
        <p>Protected content</p>
        <button type="button" onClick={() => void logout()}>
          Sign out
        </button>
      </AuthenticatedGuard>
    </div>
  );
}

function renderFlow() {
  const queryClient = createQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <FlowHarness />
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe("authentication end-to-end flow", () => {
  beforeEach(() => {
    fake.authenticated = false;
    clearAccessToken();
    vi.clearAllMocks();
  });

  it("logs in, opens a protected page, then logs out and denies it again", async () => {
    renderFlow();

    expect(await screen.findByRole("alert")).toHaveTextContent("You must sign in to view this page.");

    await userEvent.type(screen.getByLabelText("Email"), "ada@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "secret");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByText("Protected content")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /sign out/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("You must sign in to view this page.");
    expect(screen.queryByText("Protected content")).toBeNull();
  });
});
