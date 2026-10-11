/**
 * Frontend authentication API layer ({@code specs/10-P1-Identity-V1.5.md §155}).
 *
 * <p>Every Axios call for authentication lives here; page components never call Axios directly.
 * Raw token handling is centralized in {@code lib/auth/token-storage}.
 */
import { apiGet, apiPost } from "@/lib/api-client";
import {
  currentPermissionsSchema,
  currentUserSchema,
  loginResultSchema,
  type CurrentPermissions,
  type CurrentUser,
  type LoginResult,
} from "@/schemas/auth";

export async function login(username: string, password: string): Promise<LoginResult> {
  const result = await apiPost<unknown>("/auth/login", { username, password });
  return loginResultSchema.parse(result.data);
}

export async function logout(): Promise<void> {
  await apiPost("/auth/logout");
}

export async function refreshToken(): Promise<LoginResult> {
  const result = await apiPost<unknown>("/auth/refresh-token");
  return loginResultSchema.parse(result.data);
}

export async function forgotPassword(email: string): Promise<void> {
  await apiPost("/auth/forgot-password", { email });
}

export async function resetPassword(token: string, newPassword: string): Promise<void> {
  await apiPost("/auth/reset-password", { token, newPassword });
}

export async function getCurrentUser(): Promise<CurrentUser> {
  const result = await apiGet<unknown>("/me");
  return currentUserSchema.parse(result.data);
}

export async function getCurrentPermissions(): Promise<CurrentPermissions> {
  const result = await apiGet<unknown>("/me/permissions");
  return currentPermissionsSchema.parse(result.data);
}
