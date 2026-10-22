import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

vi.mock("@/lib/auth/auth-context", () => ({
  useAuth: () => ({ can: () => true, status: "authenticated", permissions: [] }),
}));

import { apiGet } from "@/lib/api-client";
import { AccessGrantAdmin } from "@/features/commerce/components/access-grant-admin";
import { AccessList } from "@/features/commerce/components/access-list";

const mockedGet = vi.mocked(apiGet);

const activeGrant = {
  id: "g1",
  userId: "user-1",
  accessPackageId: "pkg1",
  packageName: "Full access",
  startDate: "2026-10-01T00:00:00Z",
  endDate: "2026-10-31T00:00:00Z",
  status: "ACTIVE",
  active: true,
};

const inactiveGrant = {
  id: "g2",
  userId: "user-1",
  accessPackageId: "pkg2",
  packageName: "Practice pack",
  startDate: "2026-08-01T00:00:00Z",
  endDate: "2026-08-31T00:00:00Z",
  status: "ACTIVE",
  active: false,
};

const pkg = {
  id: "pkg1",
  code: "FULL",
  name: "Full access",
  description: "Every assessment",
  status: "ACTIVE",
};

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("access grant visibility", () => {
  it("shows the caller's active and inactive grants", async () => {
    mockedGet.mockResolvedValue({
      message: "Access grants retrieved",
      data: [activeGrant, inactiveGrant],
    } as never);

    renderWithClient(<AccessList />);

    expect(await screen.findByRole("article", { name: "Full access" })).toHaveTextContent(
      "Active",
    );
    const expired = screen.getByRole("article", { name: "Practice pack" });
    expect(expired).toHaveTextContent("Inactive");
  });

  it("shows an empty state when the caller has no grants", async () => {
    mockedGet.mockResolvedValue({ message: "Access grants retrieved", data: [] } as never);

    renderWithClient(<AccessList />);

    expect(await screen.findByText("No access grants yet.")).toBeInTheDocument();
  });

  it("scopes a staff lookup to the requested user and does not load all grants first", async () => {
    const user = userEvent.setup();
    mockedGet.mockImplementation((url: string) => {
      if (url === "/access-packages") {
        return Promise.resolve({ message: "Packages retrieved", data: [pkg] } as never);
      }
      if (url === "/access-grants") {
        return Promise.resolve({
          message: "Access grants retrieved",
          data: [activeGrant],
        } as never);
      }
      return Promise.reject(new Error(`Unexpected GET ${url}`));
    });

    renderWithClient(<AccessGrantAdmin />);

    await screen.findByPlaceholderText("Paste a user UUID");
    expect(mockedGet.mock.calls.filter((call) => call[0] === "/access-grants")).toHaveLength(0);

    await user.type(screen.getByPlaceholderText("Paste a user UUID"), "user-1");
    await user.click(screen.getByRole("button", { name: "Load grants" }));

    expect(await screen.findByRole("article", { name: "Full access" })).toBeInTheDocument();
    const scopedCall = mockedGet.mock.calls.find((call) => call[0] === "/access-grants");
    expect(scopedCall?.[1]).toEqual({ userId: "user-1" });
  });
});
