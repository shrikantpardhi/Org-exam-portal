"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import {
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
} from "@/features/auth/api/auth-api";
import { setUnauthorizedHandler } from "@/lib/api-client";
import { can as canPermission } from "@/lib/auth/permission";
import { clearAccessToken, getAccessToken, setAccessToken } from "@/lib/auth/token-storage";
import type { ApiClientError } from "@/lib/errors";
import type { CurrentUser } from "@/schemas/auth";

/**
 * Application authentication state ({@code specs/10-P1-Identity-V1.5.md §100, §156}).
 *
 * <p>Authentication state is one of authenticated / unauthenticated / loading / error. Current
 * user and effective UI permissions come from the backend ({@code GET /api/v1/me}); they are never
 * reconstructed from local role labels. Permissions are a UI affordance only — the backend remains
 * the security boundary.
 */
export type AuthStatus = "authenticated" | "unauthenticated" | "loading" | "error";

export interface AuthContextValue {
  status: AuthStatus;
  user: CurrentUser | null;
  permissions: string[];
  error: ApiClientError | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  can: (permission: string) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [hasToken, setHasToken] = useState<boolean>(() => getAccessToken() !== null);

  const userQuery = useQuery({
    queryKey: ["auth", "me"],
    queryFn: getCurrentUser,
    enabled: hasToken,
    retry: false,
  });

  // A 401 on any non-auth request means the session is no longer valid: clear local state.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearAccessToken();
      setHasToken(false);
      queryClient.removeQueries({ queryKey: ["auth"] });
    });
    return () => setUnauthorizedHandler(null);
  }, [queryClient]);

  const login = useCallback(
    async (username: string, password: string) => {
      const result = await loginRequest(username, password);
      setAccessToken(result.accessToken);
      setHasToken(true);
      await queryClient.invalidateQueries({ queryKey: ["auth"] });
    },
    [queryClient],
  );

  const logout = useCallback(async () => {
    try {
      await logoutRequest();
    } catch {
      // Even if the backend logout fails, local authentication state is cleared safely (§165).
    }
    clearAccessToken();
    setHasToken(false);
    queryClient.removeQueries({ queryKey: ["auth"] });
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(() => {
    const user = userQuery.data ?? null;
    const permissions = user?.permissions ?? [];

    let status: AuthStatus;
    if (!hasToken) {
      status = "unauthenticated";
    } else if (userQuery.isPending) {
      status = "loading";
    } else if (userQuery.isError) {
      status = "error";
    } else {
      status = "authenticated";
    }

    return {
      status,
      user,
      permissions,
      error: (userQuery.error as ApiClientError | null) ?? null,
      login,
      logout,
      can: (permission: string) => canPermission(permissions, permission),
    };
  }, [hasToken, userQuery.data, userQuery.isPending, userQuery.isError, userQuery.error, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
