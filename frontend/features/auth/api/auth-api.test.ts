import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}));

import { apiGet, apiPost } from "@/lib/api-client";
import {
  forgotPassword,
  getCurrentUser,
  login,
  resetPassword,
} from "@/features/auth/api/auth-api";

const mockedGet = vi.mocked(apiGet);
const mockedPost = vi.mocked(apiPost);

describe("auth api", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("posts credentials and parses the login result", async () => {
    mockedPost.mockResolvedValue({
      message: "Login successful",
      data: {
        accessToken: "jwt",
        tokenType: "Bearer",
        expiresIn: 3600,
        user: { id: "u1", firstName: "Ada", lastName: "Lovelace", roles: ["STUDENT"] },
      },
    });

    const result = await login("ada@example.com", "secret");

    expect(mockedPost).toHaveBeenCalledWith("/auth/login", {
      username: "ada@example.com",
      password: "secret",
    });
    expect(result.accessToken).toBe("jwt");
    expect(result.user.roles).toEqual(["STUDENT"]);
  });

  it("parses the current user and permissions from /me", async () => {
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

    const user = await getCurrentUser();

    expect(mockedGet).toHaveBeenCalledWith("/me");
    expect(user.permissions).toEqual(["USER_VIEW"]);
  });

  it("posts the reset token and new password", async () => {
    mockedPost.mockResolvedValue({ message: "Password has been reset", data: undefined });

    await resetPassword("token-1", "Another-Strong-Pass-1");

    expect(mockedPost).toHaveBeenCalledWith("/auth/reset-password", {
      token: "token-1",
      newPassword: "Another-Strong-Pass-1",
    });
  });

  it("posts the forgot-password email", async () => {
    mockedPost.mockResolvedValue({ message: "ok", data: undefined });

    await forgotPassword("ada@example.com");

    expect(mockedPost).toHaveBeenCalledWith("/auth/forgot-password", {
      email: "ada@example.com",
    });
  });

  it("rejects an unexpected server shape", async () => {
    mockedGet.mockResolvedValue({ message: "ok", data: { unexpected: true } });

    await expect(getCurrentUser()).rejects.toBeTruthy();
  });
});
