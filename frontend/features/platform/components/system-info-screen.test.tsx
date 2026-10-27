import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiPatch: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

import { apiGet } from "@/lib/api-client";
import { SystemInfoScreen } from "@/features/platform/components/system-info-screen";

const mockedGet = vi.mocked(apiGet);

/** The four dependencies the server reports as of this slice. */
const DEPENDENCIES = [
  { name: "application", status: "UP" },
  { name: "postgresql", status: "UP" },
  { name: "redis", status: "UP" },
  { name: "background_workers", status: "DEGRADED" },
];

function systemInfo() {
  return {
    appVersion: "1.5.0",
    frontendVersion: "1.5.0",
    // Still on the wire; deliberately not rendered any more.
    gitCommit: "abc123def456",
    buildDate: "2026-01-01T00:00:00Z",
    migrationVersion: 37,
    lastMigration: "V37__organization_profile",
    appliedOn: "2026-01-01T00:00:00Z",
    uptimeSeconds: 7200,
    dependencies: DEPENDENCIES,
  };
}

function renderScreen() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <SystemInfoScreen />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("system info screen", () => {
  it("does not render build provenance", async () => {
    mockedGet.mockResolvedValue({ data: systemInfo(), message: "ok" });
    renderScreen();

    await screen.findByText("Versions");
    expect(screen.queryByText("Git commit")).toBeNull();
    expect(screen.queryByText("Build date")).toBeNull();
    // The values must not leak through some other label either.
    expect(screen.queryByText("abc123def456")).toBeNull();
  });

  it("keeps the version and lifecycle fields the operator acts on", async () => {
    mockedGet.mockResolvedValue({ data: systemInfo(), message: "ok" });
    renderScreen();

    await screen.findByText("Versions");
    for (const label of ["Application", "Frontend", "Migration", "Applied on", "Uptime"]) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    // Application and frontend versions are both 1.5.0 in this fixture, so both rows render it.
    expect(screen.getAllByText("1.5.0")).toHaveLength(2);
    expect(screen.getByText(/V37/)).toBeInTheDocument();
    expect(screen.getByText("2h 0m")).toBeInTheDocument();
  });

  it("lists every dependency the server reports under Service status", async () => {
    mockedGet.mockResolvedValue({ data: systemInfo(), message: "ok" });
    renderScreen();

    // The card heading names the section; the table's own caption carries the same words for a
    // screen reader, so query the heading rather than the bare text.
    expect(await screen.findByRole("heading", { name: "Service status" })).toBeInTheDocument();

    const table = screen.getByRole("table");
    expect(within(table).getByText("application")).toBeInTheDocument();
    expect(within(table).getByText("postgresql")).toBeInTheDocument();
    expect(within(table).getByText("redis")).toBeInTheDocument();
    expect(within(table).getByText("background_workers")).toBeInTheDocument();
    // One status pill per dependency, so a new dependency cannot be silently dropped.
    expect(within(table).getAllByText(/^(Up|Degraded)$/)).toHaveLength(4);
  });
});
