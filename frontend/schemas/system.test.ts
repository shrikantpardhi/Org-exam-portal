import { describe, expect, it } from "vitest";

import { apiEnvelopeSchema } from "@/schemas/api";
import { systemInfoSchema } from "@/schemas/system";

describe("systemInfoSchema", () => {
  it("accepts a well-formed payload", () => {
    const parsed = systemInfoSchema.safeParse({
      application: "exam-platform-backend",
      version: "1.5.0",
      environment: "local",
      timestamp: "2026-10-07T10:00:00Z",
      requestId: "abc",
    });

    expect(parsed.success).toBe(true);
  });

  it("rejects a payload with missing required fields", () => {
    expect(systemInfoSchema.safeParse({ application: "x" }).success).toBe(false);
  });
});

describe("apiEnvelopeSchema", () => {
  it("validates the success envelope and nested data", () => {
    const schema = apiEnvelopeSchema(systemInfoSchema);

    const parsed = schema.safeParse({
      success: true,
      message: "Request processed successfully",
      data: {
        application: "exam-platform-backend",
        version: "1.5.0",
        environment: "local",
        timestamp: "2026-10-07T10:00:00Z",
        requestId: null,
      },
    });

    expect(parsed.success).toBe(true);
  });

  it("rejects an envelope that is not successful", () => {
    const schema = apiEnvelopeSchema(systemInfoSchema);

    expect(schema.safeParse({ success: false, message: "no", data: null }).success).toBe(false);
  });
});
