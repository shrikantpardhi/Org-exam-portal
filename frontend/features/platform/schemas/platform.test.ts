import { describe, expect, it } from "vitest";

import {
  changeOrganizationStatusSchema,
  createAdminSchema,
  createOrganizationSchema,
  organizationSummarySchema,
  platformStatusSchema,
  updateOrganizationSchema,
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

  it("accepts only ACTIVE or INACTIVE as a status change", () => {
    expect(changeOrganizationStatusSchema.safeParse({ status: "ACTIVE" }).success).toBe(true);
    expect(changeOrganizationStatusSchema.safeParse({ status: "INACTIVE" }).success).toBe(true);
    // ARCHIVED is a different lifecycle step and the server refuses it on this endpoint.
    expect(changeOrganizationStatusSchema.safeParse({ status: "ARCHIVED" }).success).toBe(false);
    expect(changeOrganizationStatusSchema.safeParse({ status: "active" }).success).toBe(false);
    expect(changeOrganizationStatusSchema.safeParse({}).success).toBe(false);
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

  /**
   * The organization profile (V37, ADR-0051; collected at creation by ADR-0056).
   *
   * <p>Every profile column is optional, because the columns are nullable and an organization may
   * legitimately have no public address. The bounds mirror the database columns and the backend
   * request records, so a value that passes here is never rejected or truncated by the server.
   */
  it("accepts a create body with only a code and a name", () => {
    const parsed = createOrganizationSchema.safeParse({ code: "acme", name: "Acme" });
    expect(parsed.success).toBe(true);
    // The profile is omitted rather than defaulted: "not provided" stays absent.
    expect(parsed.success && parsed.data.addressLine1).toBeUndefined();
    expect(parsed.success && parsed.data.contactEmail).toBeUndefined();
  });

  it("accepts the whole profile at creation and keeps every value", () => {
    const parsed = createOrganizationSchema.safeParse({
      code: "acme",
      name: "Acme University",
      description: "Campus",
      addressLine1: "12 Example Street",
      addressLine2: "Suite 4",
      city: "Bengaluru",
      stateRegion: "Karnataka",
      postalCode: "560001",
      country: "India",
      contactEmail: "office@example.edu",
      contactPhone: "+91 80 0000 0000",
      websiteUrl: "https://example.edu",
      logoUrl: "https://example.edu/logo.png",
    });
    expect(parsed.success).toBe(true);
    expect(parsed.success && parsed.data.stateRegion).toBe("Karnataka");
    expect(parsed.success && parsed.data.logoUrl).toBe("https://example.edu/logo.png");
  });

  it("accepts an empty profile field, because empty means 'not provided'", () => {
    // A form submits every input; refusing "" would force the operator to distinguish "never
    // filled in" from "cleared", a distinction the server deliberately collapses to NULL.
    const parsed = createOrganizationSchema.safeParse({
      code: "acme",
      name: "Acme",
      addressLine1: "",
      city: "",
      contactEmail: "",
      websiteUrl: "",
    });
    expect(parsed.success).toBe(true);
  });

  it("rejects a non-empty contact email that is not a valid address", () => {
    expect(
      createOrganizationSchema.safeParse({ code: "acme", name: "Acme", contactEmail: "nope" }).success,
    ).toBe(false);
    expect(
      createOrganizationSchema.safeParse({
        code: "acme",
        name: "Acme",
        contactEmail: "office@example.edu",
      }).success,
    ).toBe(true);
    // An empty string is the "not set" case, not an invalid address.
    expect(
      createOrganizationSchema.safeParse({ code: "acme", name: "Acme", contactEmail: "" }).success,
    ).toBe(true);
  });

  it("enforces the V37 column bounds", () => {
    const base = { code: "acme", name: "Acme" };
    expect(createOrganizationSchema.safeParse({ ...base, addressLine1: "a".repeat(200) }).success).toBe(true);
    expect(createOrganizationSchema.safeParse({ ...base, addressLine1: "a".repeat(201) }).success).toBe(false);
    expect(createOrganizationSchema.safeParse({ ...base, city: "a".repeat(100) }).success).toBe(true);
    expect(createOrganizationSchema.safeParse({ ...base, city: "a".repeat(101) }).success).toBe(false);
    expect(createOrganizationSchema.safeParse({ ...base, stateRegion: "a".repeat(101) }).success).toBe(false);
    expect(createOrganizationSchema.safeParse({ ...base, postalCode: "a".repeat(21) }).success).toBe(false);
    expect(createOrganizationSchema.safeParse({ ...base, country: "a".repeat(101) }).success).toBe(false);
    expect(createOrganizationSchema.safeParse({ ...base, contactPhone: "a".repeat(33) }).success).toBe(false);
    expect(createOrganizationSchema.safeParse({ ...base, websiteUrl: "a".repeat(501) }).success).toBe(false);
    expect(createOrganizationSchema.safeParse({ ...base, logoUrl: "a".repeat(501) }).success).toBe(false);
    // A URL-ish field is a bounded string, not a scheme-validated one: an operator may store
    // "example.edu" and the backend does not promise to accept only absolute URLs.
    expect(createOrganizationSchema.safeParse({ ...base, websiteUrl: "example.edu" }).success).toBe(true);
  });

  it("accepts a PATCH body that changes one field and omits the rest", () => {
    const parsed = updateOrganizationSchema.safeParse({ city: "Mysuru" });
    expect(parsed.success).toBe(true);
    // Omitted means "leave unchanged"; only what was sent may be written.
    expect(parsed.success && parsed.data.name).toBeUndefined();
  });

  it("accepts an empty string in a PATCH body, which is how a value is cleared", () => {
    const parsed = updateOrganizationSchema.safeParse({ contactPhone: "" });
    expect(parsed.success).toBe(true);
    expect(parsed.success && parsed.data.contactPhone).toBe("");
  });

  it("refuses a blank name in a PATCH body but allows omitting it", () => {
    // The column is NOT NULL: omitting name keeps it, sending whitespace is a mistake.
    expect(updateOrganizationSchema.safeParse({ name: "   " }).success).toBe(false);
    expect(updateOrganizationSchema.safeParse({ name: "" }).success).toBe(false);
    expect(updateOrganizationSchema.safeParse({ city: "Pune" }).success).toBe(true);
  });

  it("validates the contact email on the PATCH body too", () => {
    expect(updateOrganizationSchema.safeParse({ contactEmail: "nope" }).success).toBe(false);
    expect(updateOrganizationSchema.safeParse({ contactEmail: "" }).success).toBe(true);
    expect(updateOrganizationSchema.safeParse({}).success).toBe(true);
  });
});
