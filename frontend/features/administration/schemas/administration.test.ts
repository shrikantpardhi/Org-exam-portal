import { describe, expect, it } from "vitest";

import { createUserSchema, updateUserSchema } from "@/features/administration/schemas/administration";

describe("user form schemas", () => {
  it("requires a valid email and at least one role to create", () => {
    expect(
      createUserSchema.safeParse({ firstName: "A", lastName: "B", email: "not-an-email", roles: ["r1"] })
        .success,
    ).toBe(false);
    expect(
      createUserSchema.safeParse({ firstName: "A", lastName: "B", email: "a@b.com", roles: [] })
        .success,
    ).toBe(false);
    expect(
      createUserSchema.safeParse({ firstName: "A", lastName: "B", email: "a@b.com", roles: ["r1"] })
        .success,
    ).toBe(true);
  });

  it("requires a name and at least one role to update", () => {
    expect(updateUserSchema.safeParse({ firstName: "", lastName: "", roles: ["r1"] }).success).toBe(false);
    expect(updateUserSchema.safeParse({ firstName: "A", lastName: "B", roles: [] }).success).toBe(false);
    expect(updateUserSchema.safeParse({ firstName: "A", lastName: "B", roles: ["r1"] }).success).toBe(true);
  });
});
