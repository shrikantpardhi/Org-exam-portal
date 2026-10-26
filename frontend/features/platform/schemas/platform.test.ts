import { describe, expect, it } from "vitest";

import {
  createAdminSchema,
  createOrganizationSchema,
  organizationSummarySchema,
  platformStatusSchema,
} from "@/features/platform/schemas/platform";

describe("platform schemas", () => {
  it("uppercases and defaults a missing status to UNKNOWN", () => {
    expect(
      organizationSummarySchema.parse({ id: "1", code: "c", name: "n", status: "active" }).status,
    ).toBe("ACTIVE");
    expect(
      organizationSummarySchema.parse({ id: "1", code: "c", name: "n", status: null }).status,
    ).toBe("UNKNOWN");
  });

  it("collapses nullable status and health fields to null", () => {
    const parsed = platformStatusSchema.parse({
      organizationCount: 0,
      adminCount: 0,
      userCount: 0,
      seedStatus: null,
      systemHealth: null,
    });
    expect(parsed.seedStatus).toBeNull();
    expect(parsed.systemHealth).toBeNull();
  });

  it("requires a code and name for a new organization", () => {
    expect(createOrganizationSchema.safeParse({ code: "", name: "Acme" }).success).toBe(false);
    expect(createOrganizationSchema.safeParse({ code: "acme", name: "Acme" }).success).toBe(true);
  });

  it("requires names and a valid email for a new administrator", () => {
    expect(
      createAdminSchema.safeParse({ firstName: "A", lastName: "B", email: "not-an-email" }).success,
    ).toBe(false);
    expect(
      createAdminSchema.safeParse({ firstName: "A", lastName: "B", email: "admin@example.com" })
        .success,
    ).toBe(true);
  });
});
