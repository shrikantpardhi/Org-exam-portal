import { describe, expect, it } from "vitest";

import { roleHome, safeNext } from "@/lib/auth/role-home";

describe("roleHome", () => {
  it("routes must-change-password accounts to /change-password first", () => {
    expect(roleHome(["SUPER_ADMIN"], true)).toBe("/change-password");
    expect(roleHome(["ADMIN"], true)).toBe("/change-password");
  });

  it("routes SUPER_ADMIN to /platform", () => {
    expect(roleHome(["SUPER_ADMIN"], false)).toBe("/platform");
  });

  it("routes ADMIN to /admin", () => {
    expect(roleHome(["ADMIN"], false)).toBe("/admin");
  });

  it("routes STUDENT to /student", () => {
    expect(roleHome(["STUDENT"], false)).toBe("/student");
  });

  it("routes other roles to the generic landing", () => {
    expect(roleHome(["STAFF"], false)).toBe("/account");
  });
});

describe("safeNext", () => {
  it("accepts same-origin relative paths", () => {
    expect(safeNext("/platform/organizations")).toBe("/platform/organizations");
  });

  it("rejects protocol-relative and absolute URLs", () => {
    expect(safeNext("//evil.example")).toBeNull();
    expect(safeNext("https://evil.example")).toBeNull();
    expect(safeNext("javascript:alert(1)")).toBeNull();
  });

  it("rejects empty and missing values", () => {
    expect(safeNext("")).toBeNull();
    expect(safeNext(null)).toBeNull();
    expect(safeNext(undefined)).toBeNull();
  });
});
