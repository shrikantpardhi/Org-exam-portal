/**
 * Error normalization.
 *
 * Every failure surfaced by the API client becomes a single {@link ApiClientError} type so that
 * UI code never has to understand transport details. Internal details are never displayed to the
 * user; the correlation identifier is surfaced so a problem can be traced.
 */
import { isAxiosError } from "axios";
import type { ApiErrorEnvelope, ApiErrorItem } from "@/types/api";

export const NETWORK_ERROR_CODE = "NETWORK_ERROR";
export const UNKNOWN_ERROR_CODE = "UNKNOWN_ERROR";

export class ApiClientError extends Error {
  readonly status: number | null;
  readonly code: string;
  readonly requestId?: string;
  readonly fieldErrors: ApiErrorItem[];

  constructor(params: {
    message: string;
    status: number | null;
    code: string;
    requestId?: string;
    fieldErrors?: ApiErrorItem[];
  }) {
    super(params.message);
    this.name = "ApiClientError";
    this.status = params.status;
    this.code = params.code;
    this.requestId = params.requestId;
    this.fieldErrors = params.fieldErrors ?? [];
  }

  /** True when the request never reached the server (offline, DNS, timeout, connection refused). */
  static isNetworkError(error: unknown): boolean {
    return error instanceof ApiClientError && error.status === null;
  }

  static isUnauthorized(error: unknown): boolean {
    return error instanceof ApiClientError && error.status === 401;
  }

  static isForbidden(error: unknown): boolean {
    return error instanceof ApiClientError && error.status === 403;
  }
}

function isErrorEnvelope(value: unknown): value is ApiErrorEnvelope {
  return (
    typeof value === "object" &&
    value !== null &&
    "success" in value &&
    (value as { success?: unknown }).success === false
  );
}

/**
 * Converts any thrown value into an {@link ApiClientError}.
 *
 * Messages come from the server envelope when available; otherwise a safe generic message is
 * used. Stack traces and internal details are never propagated.
 */
export function normalizeApiError(error: unknown): ApiClientError {
  if (error instanceof ApiClientError) {
    return error;
  }

  if (isAxiosError(error)) {
    const response = error.response;

    if (!response) {
      return new ApiClientError({
        message: "Unable to reach the server. Check your connection and try again.",
        status: null,
        code: NETWORK_ERROR_CODE,
      });
    }

    const payload: unknown = response.data;
    const envelope = isErrorEnvelope(payload) ? payload : undefined;
    const requestId =
      envelope?.requestId ??
      (response.headers?.["x-request-id"] as string | undefined);

    return new ApiClientError({
      message: envelope?.message ?? "The request could not be completed.",
      status: response.status,
      code: envelope?.errors?.[0]?.code ?? "REQUEST_FAILED",
      requestId,
      fieldErrors: envelope?.errors ?? [],
    });
  }

  return new ApiClientError({
    message: "An unexpected error occurred.",
    status: null,
    code: UNKNOWN_ERROR_CODE,
  });
}
