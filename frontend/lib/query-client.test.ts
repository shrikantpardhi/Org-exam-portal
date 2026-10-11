import { describe, expect, it } from "vitest";

import { ApiClientError } from "@/lib/errors";
import { createQueryClient } from "@/lib/query-client";

describe("query client retry policy", () => {
  it("retries reads only on a network failure and only a bounded number of times", () => {
    const options = createQueryClient().getDefaultOptions().queries;
    const retry = options?.retry as (count: number, error: unknown) => boolean;

    expect(retry(0, new ApiClientError({ message: "x", status: null, code: "NETWORK_ERROR" }))).toBe(
      true,
    );
    expect(retry(2, new ApiClientError({ message: "x", status: null, code: "NETWORK_ERROR" }))).toBe(
      false,
    );
    expect(retry(0, new ApiClientError({ message: "x", status: 500, code: "SYSTEM_ERROR" }))).toBe(
      false,
    );
  });

  it("never retries mutations automatically", () => {
    expect(createQueryClient().getDefaultOptions().mutations?.retry).toBe(false);
  });
});
