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
import { StoreCatalog } from "@/features/commerce/components/store-catalog";

const mockedGet = vi.mocked(apiGet);

function renderCatalog() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <StoreCatalog />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("student store catalog", () => {
  it("renders the active packages returned by the catalog", async () => {
    mockedGet.mockResolvedValue({
      message: "Packages retrieved",
      data: [
        { id: "pkg1", code: "FULL", name: "Full access", description: null, status: "ACTIVE" },
        { id: "pkg2", code: "PRACTICE", name: "Practice pack", description: null, status: "ACTIVE" },
      ],
    } as never);

    renderCatalog();

    expect(await screen.findByRole("article", { name: "Full access" })).toBeInTheDocument();
    expect(screen.getByRole("article", { name: "Practice pack" })).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "View package" })).toHaveLength(2);
  });

  it("shows an empty state when the catalog has no active package", async () => {
    mockedGet.mockResolvedValue({ message: "Packages retrieved", data: [] } as never);

    renderCatalog();

    expect(await screen.findByText("No packages are available yet.")).toBeInTheDocument();
  });
});
