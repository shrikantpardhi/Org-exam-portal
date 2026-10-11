/**
 * The single centralized API client.
 *
 * Responsibilities (specs/09-P0-Foundation-V1.5.md §43):
 * base URL, request correlation id, authentication token boundary, common headers, and response
 * and error normalization. It contains no exam behaviour, scoring, authorization, timer
 * authority, or submission rules.
 *
 * Blind automatic retry is deliberately absent: state-changing requests must never be retried
 * implicitly (§46). Any future retry for a specific operation requires idempotency.
 */
import axios, { isAxiosError, type AxiosAdapter, type AxiosInstance } from "axios";

import { getAccessToken } from "@/lib/auth/token-storage";
import { env } from "@/lib/env";
import { normalizeApiError, type ApiClientError } from "@/lib/errors";
import type { ApiEnvelope, ApiResult } from "@/types/api";

export const REQUEST_ID_HEADER = "X-Request-Id";
export const API_VERSION_PREFIX = "/api/v1";

/** Supplies the current bearer token, or null when unauthenticated. */
export type TokenProvider = () => string | null;

export interface ApiClientOptions {
  baseURL?: string;
  tokenProvider?: TokenProvider;
  /** Called on a 401 for a non-authentication request (for example, an expired session). */
  onUnauthorized?: (error: ApiClientError) => void;
  /** Injectable transport, used by tests to avoid real network calls. */
  adapter?: AxiosAdapter;
}

function newRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `req-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

export function createApiClient(options: ApiClientOptions = {}): AxiosInstance {
  const instance = axios.create({
    baseURL: options.baseURL ?? `${env.NEXT_PUBLIC_API_BASE_URL}${API_VERSION_PREFIX}`,
    timeout: 15_000,
    headers: { Accept: "application/json" },
    ...(options.adapter ? { adapter: options.adapter } : {}),
  });

  instance.interceptors.request.use((config) => {
    config.headers.set(REQUEST_ID_HEADER, newRequestId());

    const token = options.tokenProvider?.();
    if (token) {
      config.headers.set("Authorization", `Bearer ${token}`);
    }
    return config;
  });

  instance.interceptors.response.use(
    (response) => response,
    (error: unknown) => {
      const normalized = normalizeApiError(error);
      if (normalized.status === 401 && !isAuthenticationRequest(error)) {
        options.onUnauthorized?.(normalized);
      }
      return Promise.reject(normalized);
    },
  );

  return instance;
}

/** Authentication endpoints may legitimately return 401 without meaning the session expired. */
function isAuthenticationRequest(error: unknown): boolean {
  if (!isAxiosError(error)) {
    return false;
  }
  const url = error.config?.url;
  return typeof url === "string" && url.includes("/auth/");
}

let unauthorizedHandler: (() => void) | null = null;

/** Registers the single handler invoked when a request is rejected with 401. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

/** Application-wide client. The bearer token is read from the single token store. */
export const apiClient = createApiClient({
  tokenProvider: getAccessToken,
  onUnauthorized: () => unauthorizedHandler?.(),
});

function toResult<T>(envelope: ApiEnvelope<T>): ApiResult<T> {
  return {
    data: envelope.data,
    message: envelope.message,
    pagination: envelope.pagination,
  };
}

export async function apiGet<T>(
  url: string,
  params?: Record<string, unknown>,
): Promise<ApiResult<T>> {
  const response = await apiClient.get<ApiEnvelope<T>>(url, { params });
  return toResult(response.data);
}

export async function apiPost<T>(url: string, body?: unknown): Promise<ApiResult<T>> {
  const response = await apiClient.post<ApiEnvelope<T>>(url, body);
  return toResult(response.data);
}

export async function apiPut<T>(url: string, body?: unknown): Promise<ApiResult<T>> {
  const response = await apiClient.put<ApiEnvelope<T>>(url, body);
  return toResult(response.data);
}

/** Sends a DELETE (archive) request. Archive responses carry no body (HTTP 204). */
export async function apiDelete(url: string): Promise<void> {
  await apiClient.delete(url);
}
