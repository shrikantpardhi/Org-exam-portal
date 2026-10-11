import { describe, expect, it } from "vitest";
import {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "axios";

import {
  ApiClientError,
  NETWORK_ERROR_CODE,
  UNKNOWN_ERROR_CODE,
  normalizeApiError,
} from "@/lib/errors";

function axiosConfig(): InternalAxiosRequestConfig {
  return { headers: new AxiosHeaders() } as InternalAxiosRequestConfig;
}

function axiosErrorWithResponse(status: number, data: unknown, headers: Record<string, string> = {}) {
  const config = axiosConfig();
  const response: AxiosResponse = {
    data,
    status,
    statusText: "Error",
    headers: headers as unknown as AxiosResponse["headers"],
    config,
  };
  return new AxiosError("Request failed", "ERR_BAD_REQUEST", config, undefined, response);
}

describe("normalizeApiError", () => {
  it("reads the server error envelope, field errors and request id", () => {
    const normalized = normalizeApiError(
      axiosErrorWithResponse(422, {
        success: false,
        message: "Validation failed",
        errors: [{ field: "title", code: "INVALID_VALUE", message: "must not be blank" }],
        requestId: "req-9",
      }),
    );

    expect(normalized).toBeInstanceOf(ApiClientError);
    expect(normalized.status).toBe(422);
    expect(normalized.code).toBe("INVALID_VALUE");
    expect(normalized.message).toBe("Validation failed");
    expect(normalized.requestId).toBe("req-9");
    expect(normalized.fieldErrors).toHaveLength(1);
  });

  it("falls back to the response header for the request id", () => {
    const normalized = normalizeApiError(
      axiosErrorWithResponse(403, { success: false, message: "Access denied" }, {
        "x-request-id": "req-header",
      }),
    );

    expect(normalized.status).toBe(403);
    expect(normalized.requestId).toBe("req-header");
  });

  it("classifies a request that never reached the server as a network error", () => {
    const normalized = normalizeApiError(
      new AxiosError("Network Error", "ERR_NETWORK", axiosConfig()),
    );

    expect(normalized.status).toBeNull();
    expect(normalized.code).toBe(NETWORK_ERROR_CODE);
    expect(ApiClientError.isNetworkError(normalized)).toBe(true);
  });

  it("normalizes an unknown thrown value without leaking details", () => {
    const normalized = normalizeApiError("something odd");

    expect(normalized.status).toBeNull();
    expect(normalized.code).toBe(UNKNOWN_ERROR_CODE);
    expect(normalized.message).toBe("An unexpected error occurred.");
  });

  it("does not re-wrap an already normalized error", () => {
    const original = new ApiClientError({ message: "x", status: 400, code: "C" });

    expect(normalizeApiError(original)).toBe(original);
  });
});
