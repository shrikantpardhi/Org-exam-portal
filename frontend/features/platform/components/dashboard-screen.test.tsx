import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

import { apiGet } from "@/lib/api-client";
import { DashboardScreen } from "@/features/platform/components/dashboard-screen";

const mockedGet = vi.mocked(apiGet);

function renderDashboard() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <DashboardScreen />
    </QueryClientProvider>,
  );
}

function systemData() {
  return {
    appVersion: "1.5.0",
    frontendVersion: "1.5.0",
    gitCommit: "abc123",
    buildDate: null,
    migrationVersion: 12,
    lastMigration: "V12__seed.sql",
    appliedOn: "2026-01-01T00:00:00Z",
    uptimeSeconds: 7200,
    dependencies: [],
  };
}

function statusData(counts: { organizationCount: number; adminCount: number; userCount: number }) {
  return {
    organizationCount: counts.organizationCount,
    adminCount: counts.adminCount,
    userCount: counts.userCount,
    seedStatus: "COMPLETE",
    systemHealth: "UP",
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("platform dashboard", () => {
  it("prompts to create the first organization when none exist", async () => {
    mockedGet.mockImplementation((url) =>
      Promise.resolve({
        data: url === "/platform/status"
          ? statusData({ organizationCount: 0, adminCount: 0, userCount: 0 })
          : systemData(),
        message: "ok",
      } as never),
    );

    renderDashboard();

    await screen.findByText(/No business organization yet/);
    const createLink = screen.getByRole("link", { name: /Create organization/ });
    expect(createLink.getAttribute("href")).toBe("/platform/organizations/new");
  });

  it("shows live counts and the system summary when organizations exist", async () => {
    mockedGet.mockImplementation((url) =>
      Promise.resolve({
        data: url === "/platform/status"
          ? statusData({ organizationCount: 3, adminCount: 2, userCount: 120 })
          : systemData(),
        message: "ok",
      } as never),
    );

    renderDashboard();

    await screen.findByText("Administrators");
    expect(screen.getByText("3")).toBeTruthy();
    expect(screen.getByText("120")).toBeTruthy();
    expect(screen.getByText("Application")).toBeTruthy();
  });

  it("shows the retry affordance when the platform call fails", async () => {
    mockedGet.mockRejectedValue(new Error("boom"));

    renderDashboard();

    await screen.findByText(/Platform status could not be loaded/);
    expect(screen.getByRole("button", { name: "Retry" })).toBeTruthy();
  });
});
