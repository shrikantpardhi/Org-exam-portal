import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  createOrganization,
  createOrganizationAdmin,
  getOrganization,
  getPlatformStatus,
  getPlatformSystem,
  getProviders,
  listOrganizations,
} from "@/features/platform/api/platform-api";
import { apiGet, apiPost } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

const apiGetMock = vi.mocked(apiGet);
const apiPostMock = vi.mocked(apiPost);

const organizationSummary = {
  id: "org-1",
  code: "acme",
  name: "Acme University",
  status: "ACTIVE",
};

/**
 * Frozen platform (SUPER_ADMIN) routes (specs/05-API-Contract-V1.5.md §72).
 *
 * Every call must use a route that exists in the frozen contract: no invented endpoint, and lists
 * must stay server-paginated rather than loading an unbounded dataset.
 */
describe("platform API", () => {
  beforeEach(() => {
    apiGetMock.mockReset();
    apiPostMock.mockReset();
  });

  it("lists organizations through the frozen route with server paging", async () => {
    apiGetMock.mockResolvedValue({
      data: [organizationSummary],
      message: "ok",
      pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
    });

    const page = await listOrganizations({
      status: "ACTIVE",
      search: "acme",
      sort: "name,asc",
      page: 0,
      size: 20,
    });

    expect(apiGetMock.mock.calls[0]![0]).toBe("/platform/organizations");
    expect(apiGetMock.mock.calls[0]![1]).toMatchObject({
      status: "ACTIVE",
      search: "acme",
      sort: "name,asc",
      page: 0,
      size: 20,
    });
    expect(page.items).toHaveLength(1);
    expect(page.items[0]!.code).toBe("acme");
    expect(page.totalElements).toBe(1);
  });

  it("creates an organization through the frozen route", async () => {
    apiPostMock.mockResolvedValue({ data: organizationSummary, message: "ok" });

    const created = await createOrganization({
      code: "acme",
      name: "Acme University",
      description: "Campus",
    });

    expect(apiPostMock.mock.calls[0]![0]).toBe("/platform/organizations");
    expect(apiPostMock.mock.calls[0]![1]).toMatchObject({ code: "acme", name: "Acme University" });
    expect(created.id).toBe("org-1");
  });

  it("reads an organization detail through the id-scoped route", async () => {
    apiGetMock.mockResolvedValue({
      data: { ...organizationSummary, description: "Campus", adminCount: 1, userCount: 12 },
      message: "ok",
    });

    const detail = await getOrganization("org-1");

    expect(apiGetMock.mock.calls[0]![0]).toBe("/platform/organizations/org-1");
    expect(detail.adminCount).toBe(1);
    expect(detail.userCount).toBe(12);
  });

  it("creates the organization admin through the frozen route", async () => {
    apiPostMock.mockResolvedValue({
      data: { userId: "u-1", email: "admin@example.com", invitationSentAt: "2026-01-01T00:00:00Z" },
      message: "ok",
    });

    const admin = await createOrganizationAdmin("org-1", {
      firstName: "Ada",
      lastName: "Admin",
      email: "admin@example.com",
    });

    expect(apiPostMock.mock.calls[0]![0]).toBe("/platform/organizations/org-1/admins");
    expect(admin.email).toBe("admin@example.com");
  });

  it("reads masked provider configuration", async () => {
    apiGetMock.mockResolvedValue({
      data: {
        email: {
          configured: true,
          providerName: "SMTP",
          fromAddress: "noreply@example.com",
          updatedAt: null,
        },
        payment: {
          configured: false,
          providerName: null,
          webhookSecretSet: false,
          updatedAt: null,
        },
      },
      message: "ok",
    });

    const providers = await getProviders();

    expect(apiGetMock.mock.calls[0]![0]).toBe("/platform/providers");
    expect(providers.email.configured).toBe(true);
    expect(providers.payment.webhookSecretSet).toBe(false);
  });

  it("reads platform status and collapses missing fields", async () => {
    apiGetMock.mockResolvedValue({
      data: {
        organizationCount: 3,
        adminCount: 3,
        userCount: 120,
        seedStatus: null,
        systemHealth: "UP",
      },
      message: "ok",
    });

    const status = await getPlatformStatus();

    expect(apiGetMock.mock.calls[0]![0]).toBe("/platform/status");
    expect(status.organizationCount).toBe(3);
    expect(status.seedStatus).toBeNull();
  });

  it("reads system info and normalizes a missing migration version", async () => {
    apiGetMock.mockResolvedValue({
      data: {
        appVersion: "1.5.0",
        frontendVersion: "1.5.0",
        gitCommit: "abc123",
        buildDate: null,
        migrationVersion: null,
        lastMigration: null,
        appliedOn: null,
        uptimeSeconds: 7200,
        dependencies: [{ name: "postgres", status: "UP" }],
      },
      message: "ok",
    });

    const system = await getPlatformSystem();

    expect(apiGetMock.mock.calls[0]![0]).toBe("/platform/system");
    expect(system.migrationVersion).toBeNull();
    expect(system.dependencies).toHaveLength(1);
    expect(system.dependencies[0]!.status).toBe("UP");
  });
});
