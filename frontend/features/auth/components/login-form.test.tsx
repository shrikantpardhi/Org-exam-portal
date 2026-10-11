import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

import { LoginForm } from "@/features/auth/components/login-form";
import { apiGet, apiPost } from "@/lib/api-client";
import { AuthProvider } from "@/lib/auth/auth-context";
import { clearAccessToken } from "@/lib/auth/token-storage";
import { createQueryClient } from "@/lib/query-client";

const mockedGet = vi.mocked(apiGet);
const mockedPost = vi.mocked(apiPost);

function renderLoginForm() {
  const queryClient = createQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <LoginForm />
      </AuthProvider>
    </QueryClientProvider>,
  );
}

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

  it("submits valid credentials", async () => {
    mockedPost.mockResolvedValue({
      message: "Login successful",
      data: {
        accessToken: "jwt",
        tokenType: "Bearer",
        expiresIn: 3600,
        user: { id: "u1", firstName: "Ada", lastName: "Lovelace", roles: ["STUDENT"] },
      },
    });
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
    expect(screen.queryByRole("alert")).toBeNull();
  });
});
