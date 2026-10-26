import { beforeEach, describe, expect, it, vi } from "vitest";

import { createUser, getUser, updateUser } from "@/features/administration/api/administration-api";
import { apiGet, apiPost, apiPut } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

const apiGetMock = vi.mocked(apiGet);
const apiPostMock = vi.mocked(apiPost);
const apiPutMock = vi.mocked(apiPut);

const userDetail = {
  id: "u1",
  organizationId: "org-1",
  firstName: "Ada",
  lastName: "Lovelace",
  email: "ada@example.com",
  mobile: null,
  status: "ACTIVE",
  roles: ["STAFF"],
  createdOn: "2026-01-01T00:00:00Z",
  updatedOn: null,
};

describe("administration user API", () => {
  beforeEach(() => {
    apiGetMock.mockReset();
    apiPostMock.mockReset();
    apiPutMock.mockReset();
  });

  it("creates a user through the frozen route without status or password", async () => {
    apiPostMock.mockResolvedValue({ data: userDetail, message: "ok" });

    const created = await createUser({
      firstName: "Ada",
      lastName: "Lovelace",
      email: "ada@example.com",
      mobile: "",
      roles: ["role-1"],
    });

    expect(apiPostMock.mock.calls[0]![0]).toBe("/users");
    expect(apiPostMock.mock.calls[0]![1]).toMatchObject({
      firstName: "Ada",
      lastName: "Lovelace",
      email: "ada@example.com",
      roles: ["role-1"],
    });
    // The frozen create body has no status (a new account is ACTIVE) and no password (§56, §58).
    expect(apiPostMock.mock.calls[0]![1]).not.toHaveProperty("status");
    expect(apiPostMock.mock.calls[0]![1]).not.toHaveProperty("password");
    expect(created.id).toBe("u1");
  });

  it("updates only permitted fields through the id-scoped route", async () => {
    apiPutMock.mockResolvedValue({ data: { ...userDetail, firstName: "Augusta" }, message: "ok" });

    const updated = await updateUser("u1", {
      firstName: "Augusta",
      lastName: "Lovelace",
      mobile: "",
      roles: ["role-1"],
    });

    expect(apiPutMock.mock.calls[0]![0]).toBe("/users/u1");
    expect(apiPutMock.mock.calls[0]![1]).toMatchObject({ firstName: "Augusta", roles: ["role-1"] });
    // Email is immutable and status changes only through archive.
    expect(apiPutMock.mock.calls[0]![1]).not.toHaveProperty("email");
    expect(apiPutMock.mock.calls[0]![1]).not.toHaveProperty("status");
    expect(updated.firstName).toBe("Augusta");
  });

  it("reads a user detail through the id-scoped route", async () => {
    apiGetMock.mockResolvedValue({ data: userDetail, message: "ok" });

    const detail = await getUser("u1");

    expect(apiGetMock.mock.calls[0]![0]).toBe("/users/u1");
    expect(detail.roles).toEqual(["STAFF"]);
  });
});
