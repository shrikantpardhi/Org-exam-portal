import { describe, expect, it } from "vitest";

import { can } from "@/lib/auth/permission";

describe("permission helper", () => {
  it("returns true only when the permission is present", () => {
    expect(can(["USER_VIEW", "USER_CREATE"], "USER_VIEW")).toBe(true);
    expect(can(["USER_VIEW"], "USER_ARCHIVE")).toBe(false);
  });

  it("treats missing or empty permission sets as no access", () => {
    expect(can(undefined, "USER_VIEW")).toBe(false);
    expect(can(null, "USER_VIEW")).toBe(false);
    expect(can([], "USER_VIEW")).toBe(false);
  });
});
