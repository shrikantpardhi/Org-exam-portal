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
}));

import { ChangePasswordForm } from "@/features/auth/components/change-password-form";
import { apiGet, apiPost } from "@/lib/api-client";
import { AuthProvider } from "@/lib/auth/auth-context";
import { clearAccessToken, setAccessToken } from "@/lib/auth/token-storage";
import { createQueryClient } from "@/lib/query-client";

const mockedGet = vi.mocked(apiGet);
const mockedPost = vi.mocked(apiPost);

function renderForm() {
  const queryClient = createQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ChangePasswordForm />
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe("ChangePasswordForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearAccessToken();
  });

  it("rejects mismatched confirmation without calling the API", async () => {
    renderForm();
    await userEvent.type(screen.getByLabelText("Current password"), "old");
    await userEvent.type(screen.getByLabelText("New password"), "newpass");
    await userEvent.type(screen.getByLabelText("Confirm new password"), "different");
    await userEvent.click(screen.getByRole("button", { name: /change password/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent("do not match");
    expect(mockedPost).not.toHaveBeenCalled();
  });

  it("submits the change and redirects to the role home", async () => {
    setAccessToken("jwt");
    mockedGet.mockResolvedValue({
      message: "ok",
      data: {
        id: "u1",
        firstName: "Ada",
        lastName: "Lovelace",
        email: "ada@example.com",
        roles: ["ADMIN"],
        permissions: [],
        mustChangePassword: true,
      },
    });
    mockedPost.mockResolvedValue({ message: "changed", data: null });

    renderForm();

    await userEvent.type(screen.getByLabelText("Current password"), "old");
    await userEvent.type(screen.getByLabelText("New password"), "newpass123");
    await userEvent.type(screen.getByLabelText("Confirm new password"), "newpass123");
    await userEvent.click(screen.getByRole("button", { name: /change password/i }));

    await waitFor(() =>
      expect(mockedPost).toHaveBeenCalledWith("/auth/change-password", {
        currentPassword: "old",
        newPassword: "newpass123",
      }),
    );
    await waitFor(() => expect(push).toHaveBeenCalledWith("/admin"));
  });
});
