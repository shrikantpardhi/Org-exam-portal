import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

import { LoginForm } from "@/features/auth/components/login-form";
import { apiGet, apiPost } from "@/lib/api-client";
import { AuthProvider } from "@/lib/auth/auth-context";
import { clearAccessToken } from "@/lib/auth/token-storage";
import { createQueryClient } from "@/lib/query-client";

const mockedGet = vi.mocked(apiGet);
const mockedPost = vi.mocked(apiPost);

function renderLoginForm(portal?: "SYSTEM_ADMIN" | "ORGANIZATION_ADMIN" | "STUDENT") {
  const queryClient = createQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <LoginForm portal={portal} />
      </AuthProvider>
    </QueryClientProvider>,
  );
}

const meResponse = {
  message: "Current user retrieved",
  data: {
    id: "u1",
    firstName: "Ada",
    lastName: "Lovelace",
    email: "ada@example.com",
    roles: ["STUDENT"],
    permissions: [],
    mustChangePassword: false,
  },
};

const loginResponse = (roles: string[], mustChangePassword = false) => ({
  message: "Login successful",
  data: {
    accessToken: "jwt",
    tokenType: "Bearer",
    expiresIn: 3600,
    user: { id: "u1", firstName: "Ada", lastName: "Lovelace", roles, mustChangePassword },
  },
});

describe("LoginForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearAccessToken();
  });

  it("shows a generic error when the credentials are rejected", async () => {
    mockedPost.mockRejectedValue(new Error("unauthorized"));
    mockedGet.mockRejectedValue(new Error("unauthorized"));
    renderLoginForm();

    await userEvent.type(screen.getByLabelText("Email"), "ada@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "wrong-password");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid username or password.");
  });

  it("validates required fields without calling the API", async () => {
    renderLoginForm();

    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Enter your username and password.",
    );
    expect(mockedPost).not.toHaveBeenCalled();
  });

  it("submits valid credentials without a portal and redirects to the role home", async () => {
    mockedPost.mockResolvedValue(loginResponse(["STUDENT"]));
    mockedGet.mockResolvedValue(meResponse);
    renderLoginForm();

    await userEvent.type(screen.getByLabelText("Email"), "ada@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "secret");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() =>
      expect(mockedPost).toHaveBeenCalledWith("/auth/login", {
        username: "ada@example.com",
        password: "secret",
      }),
    );
    await waitFor(() => expect(push).toHaveBeenCalledWith("/student"));
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("sends the selected portal and redirects a SUPER_ADMIN to /platform", async () => {
    mockedPost.mockResolvedValue(loginResponse(["SUPER_ADMIN"]));
    mockedGet.mockResolvedValue(meResponse);
    renderLoginForm("SYSTEM_ADMIN");

    await userEvent.type(screen.getByLabelText("Email"), "root@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "secret");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() =>
      expect(mockedPost).toHaveBeenCalledWith("/auth/login", {
        username: "root@example.com",
        password: "secret",
        portal: "SYSTEM_ADMIN",
      }),
    );
    await waitFor(() => expect(push).toHaveBeenCalledWith("/platform"));
  });

  it("routes a must-change-password account to /change-password", async () => {
    mockedPost.mockResolvedValue(loginResponse(["ADMIN"], true));
    mockedGet.mockResolvedValue(meResponse);
    renderLoginForm("ORGANIZATION_ADMIN");

    await userEvent.type(screen.getByLabelText("Email"), "admin@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "secret");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/change-password"));
  });

  it("shows the generic portal-mismatch message and never names the correct portal", async () => {
    const { ApiClientError } = await import("@/lib/errors");
    mockedPost.mockRejectedValue(
      new ApiClientError({ message: "This account cannot use this portal", status: 401, code: "PORTAL_MISMATCH" }),
    );
    mockedGet.mockRejectedValue(new Error("unauthorized"));
    renderLoginForm("SYSTEM_ADMIN");

    await userEvent.type(screen.getByLabelText("Email"), "student@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "secret");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("This account cannot use this portal.");
    expect(alert).not.toHaveTextContent(/student|organization|system admin/i);
  });

  it("lists the other portals as a wrong-portal hint", () => {
    renderLoginForm("STUDENT");

    expect(screen.getByRole("link", { name: "System admin" })).toHaveAttribute("href", "/login/admin");
    expect(screen.getByRole("link", { name: "Organization admin" })).toHaveAttribute(
      "href",
      "/login/organization",
    );
  });
});
