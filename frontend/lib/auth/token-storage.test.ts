import { beforeEach, describe, expect, it } from "vitest";

import {
  ACCESS_TOKEN_STORAGE_KEY,
  clearAccessToken,
  getAccessToken,
  setAccessToken,
} from "@/lib/auth/token-storage";

describe("token storage", () => {
  beforeEach(() => {
    clearAccessToken();
    window.sessionStorage.clear();
  });

  it("stores and reads the access token from a single documented key", () => {
    setAccessToken("token-abc");

    expect(getAccessToken()).toBe("token-abc");
    expect(window.sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)).toBe("token-abc");
  });

  it("clears the token", () => {
    setAccessToken("token-abc");
    clearAccessToken();

    expect(getAccessToken()).toBeNull();
    expect(window.sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)).toBeNull();
  });
});
