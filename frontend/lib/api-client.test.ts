import { describe, expect, it, vi } from "vitest";
import { AxiosError, type AxiosAdapter, type AxiosResponse } from "axios";

import { REQUEST_ID_HEADER, createApiClient } from "@/lib/api-client";
import { ApiClientError } from "@/lib/errors";

function unauthorizedAdapter(message: string): AxiosAdapter {
  return async (config): Promise<AxiosResponse> => {
    const response: AxiosResponse = {
      data: { success: false, message },
      status: 401,
      statusText: "Unauthorized",
      headers: {},
      config,
    };
    throw new AxiosError(message, AxiosError.ERR_BAD_REQUEST, config, null, response);
  };
}

function okAdapter(payload: unknown, capture: (config: unknown) => void): AxiosAdapter {
  return async (config): Promise<AxiosResponse> => {
    capture(config);
    return {
      data: payload,
      status: 200,
      statusText: "OK",
      headers: {},
      config,
    };
  };
}

describe("api client", () => {
  it("adds a request correlation id and the bearer token to every request", async () => {
    let captured: { headers: { get: (name: string) => unknown } } | undefined;
    const client = createApiClient({
      baseURL: "http://api.test/api/v1",
      tokenProvider: () => "token-123",
      adapter: okAdapter(
        { success: true, message: "ok", data: { value: 1 } },
        (config) => {
          captured = config as typeof captured;
        },
      ),
    });

    const response = await client.get("/system/info");

    expect(captured?.headers.get(REQUEST_ID_HEADER)).toBeTruthy();
    expect(captured?.headers.get("Authorization")).toBe("Bearer token-123");
    expect(response.data).toEqual({ success: true, message: "ok", data: { value: 1 } });
  });

  it("omits the authorization header when there is no token", async () => {
    let captured: { headers: { get: (name: string) => unknown } } | undefined;
    const client = createApiClient({
      baseURL: "http://api.test/api/v1",
      tokenProvider: () => null,
      adapter: okAdapter(
        { success: true, message: "ok", data: null },
        (config) => {
          captured = config as typeof captured;
        },
      ),
    });

    await client.get("/system/info");

    expect(captured?.headers.get("Authorization")).toBeUndefined();
  });

  it("defaults to a same-origin relative base path", () => {
    // The regression this guards (GAP-164): the built bundle must not hard-code an absolute
    // origin, or the app only works for the one host it was built with.
    const client = createApiClient({});

    expect(client.defaults.baseURL).toBe("/api/v1");
    expect(client.defaults.baseURL).not.toMatch(/^https?:\/\//);
  });

  it("honours an explicit base URL override", () => {
    const client = createApiClient({ baseURL: "http://api.test/api/v1" });

    expect(client.defaults.baseURL).toBe("http://api.test/api/v1");
  });

  it("invokes the unauthorized handler on a 401 for a non-auth request", async () => {
    const onUnauthorized = vi.fn();
    const client = createApiClient({
      baseURL: "http://api.test/api/v1",
      onUnauthorized,
      adapter: unauthorizedAdapter("Authentication required"),
    });

    await expect(client.get("/users")).rejects.toBeInstanceOf(ApiClientError);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("does not invoke the unauthorized handler for auth endpoints", async () => {
    const onUnauthorized = vi.fn();
    const client = createApiClient({
      baseURL: "http://api.test/api/v1",
      onUnauthorized,
      adapter: unauthorizedAdapter("Invalid credentials"),
    });

    await expect(client.post("/auth/login", {})).rejects.toBeInstanceOf(ApiClientError);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });
});
