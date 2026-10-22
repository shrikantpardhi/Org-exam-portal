import { describe, expect, it } from "vitest";

import {
  announcementSchema,
  emailTemplateSchema,
  notificationSchema,
  renderTemplatePreview,
} from "@/features/communication/schemas/communication";

describe("communication schemas", () => {
  it("parses a notification and defaults an unknown status safely", () => {
    const parsed = notificationSchema.parse({
      id: "n1",
      title: "Welcome",
      message: "Hello",
      notificationStatus: "unread",
      createdOn: "2026-10-22T10:00:00Z",
    });

    expect(parsed.notificationStatus).toBe("UNREAD");
    expect(parsed.message).toBe("Hello");

    const missing = notificationSchema.parse({ id: "n2", title: "No status" });
    expect(missing.notificationStatus).toBe("UNKNOWN");
  });

  it("parses a template and preserves nullable fields", () => {
    const template = emailTemplateSchema.parse({
      id: "t1",
      code: "WELCOME",
      name: "Welcome email",
      subject: null,
      body: null,
      status: "ACTIVE",
      extra: "ignored",
    });

    expect(template.subject).toBeNull();
    expect(template.status).toBe("ACTIVE");
    expect(template).not.toHaveProperty("extra");
  });

  it("parses an announcement window", () => {
    const announcement = announcementSchema.parse({
      id: "a1",
      title: "Maintenance",
      description: "Tonight",
      startDate: "2026-10-22T00:00:00Z",
      endDate: null,
      status: "INACTIVE",
    });

    expect(announcement.endDate).toBeNull();
    expect(announcement.status).toBe("INACTIVE");
  });

  it("renders only known preview placeholders and preserves unknown ones", () => {
    const rendered = renderTemplatePreview(
      "Hi {{studentName}} from {{organizationName}} about {{assessmentName}} and {{unknownVariable}}",
    );

    expect(rendered).toContain("Hi Sample Student from Demo Organization");
    expect(rendered).toContain("about SSC Mock Test 01");
    expect(rendered).toContain("{{unknownVariable}}");
  });
});
