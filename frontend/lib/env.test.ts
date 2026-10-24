// Regression tests for the API base URL resolution (GAP-164).
//
// The failure these lock down: an absolute origin inlined at build time works only for the exact
// host the image was built with. Every other host makes the call cross-origin, the edge's
// `connect-src 'self'` policy blocks it, and the user sees "Unable to reach the server."

import { describe, expect, it } from "vitest";

import { API_VERSION_PREFIX, resolveApiBaseUrl } from "@/lib/env";

describe("resolveApiBaseUrl", () => {
  it("defaults to the same-origin version prefix when unset", () => {
    expect(resolveApiBaseUrl(undefined)).toBe("/api/v1");
    expect(resolveApiBaseUrl("")).toBe("/api/v1");
    expect(resolveApiBaseUrl("   ")).toBe("/api/v1");
  });

  it("keeps an explicit relative base relative so it follows the page origin", () => {
    expect(resolveApiBaseUrl("/api/v1")).toBe("/api/v1");
    expect(resolveApiBaseUrl("/api/v1/")).toBe("/api/v1");
    expect(resolveApiBaseUrl("/")).toBe("/api/v1");
  });

  it("appends the version prefix to an absolute origin exactly once", () => {
    expect(resolveApiBaseUrl("http://localhost:8081")).toBe("http://localhost:8081/api/v1");
    expect(resolveApiBaseUrl("http://localhost:8081/")).toBe("http://localhost:8081/api/v1");
    expect(resolveApiBaseUrl("https://exam.example.test")).toBe(
      "https://exam.example.test/api/v1",
    );
  });

  it("does not double the prefix when the deployment already includes it", () => {
    expect(resolveApiBaseUrl("http://localhost:8081/api/v1")).toBe(
      "http://localhost:8081/api/v1",
    );
    expect(resolveApiBaseUrl("http://localhost:8081/api/v1/")).toBe(
      "http://localhost:8081/api/v1",
    );
  });

  it("never emits an empty or prefix-less base", () => {
    for (const input of [undefined, "", "/", "http://h:1", "http://h:1/api/v1"]) {
      expect(resolveApiBaseUrl(input)).toContain(API_VERSION_PREFIX);
    }
  });

  it("rejects the historical absolute-origin default that broke other hosts", () => {
    // A relative base is what makes the image host-independent. This asserts the property, not a
    // particular string, so a future default can change without silently reintroducing the bug.
    const resolved = resolveApiBaseUrl(undefined);
    expect(resolved.startsWith("/")).toBe(true);
    expect(resolved).not.toMatch(/^https?:\/\//);
  });
});
