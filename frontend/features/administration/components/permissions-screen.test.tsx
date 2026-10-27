import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
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

import { apiGet } from "@/lib/api-client";
import { PermissionsScreen } from "@/features/administration/components/permissions-screen";

const mockedGet = vi.mocked(apiGet);

const permissions = [
  { id: "p1", code: "ROLE_VIEW", name: "View roles", description: "See roles", status: "ACTIVE" },
  { id: "p2", code: "USER_CREATE", name: "Create user", description: null, status: "ACTIVE" },
  { id: "p3", code: "AUDIT_VIEW", name: "View audit", description: "See the trail", status: "INACTIVE" },
];

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

function paged(page: number, size: number, total: number) {
  return {
    message: "Permissions retrieved",
    data: permissions,
    pagination: { page, size, totalElements: total, totalPages: Math.max(1, Math.ceil(total / size)) },
  } as never;
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedGet.mockImplementation(() => Promise.resolve(paged(0, 50, 169)));
});

/**
 * The permission catalogue is fixed master data (~170 rows). This screen is for scanning it, so it
 * must page honestly: the old version fetched one hard-coded page of 200 and rendered a footer that
 * always read "Page 1 of 1", which told an operator nothing about where the catalogue ended.
 */
describe("PermissionsScreen paging", () => {
  it("requests the operator-chosen page size rather than a hard-coded one", async () => {
    renderWithClient(<PermissionsScreen />);
    await waitFor(() => {
      expect(mockedGet).toHaveBeenCalledWith("/permissions", expect.objectContaining({ page: 0, size: 50 }));
    });
  });

  it("shows the real catalogue size, not the size of one screenful", async () => {
    renderWithClient(<PermissionsScreen />);
    await waitFor(() => {
      // 169 total, 3 on this page.
      expect(screen.getByText(/Showing 1–3 of 169/)).toBeInTheDocument();
    });
  });

  it("re-requests with the new size when the operator changes rows per page", async () => {
    const user = userEvent.setup();
    renderWithClient(<PermissionsScreen />);
    await waitFor(() => expect(screen.getByLabelText("Rows per page")).toBeInTheDocument());

    await user.selectOptions(screen.getByLabelText("Rows per page"), "10");

    await waitFor(() => {
      expect(mockedGet).toHaveBeenCalledWith("/permissions", expect.objectContaining({ size: 10 }));
    });
  });

  it("offers previous/next navigation and disables it on a single page", async () => {
    mockedGet.mockImplementation(() => Promise.resolve(paged(0, 50, 3)));
    renderWithClient(<PermissionsScreen />);

    // One page of three rows: both directions are inert, and the label says so.
    await waitFor(() => expect(screen.getByText("Page 1 of 1")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  });

  it("fetches the next page when Next is pressed", async () => {
    const user = userEvent.setup();
    mockedGet.mockImplementation((_url: string, params?: Record<string, unknown>) =>
      Promise.resolve(paged(Number(params?.page ?? 0), 50, 169)),
    );
    renderWithClient(<PermissionsScreen />);

    await waitFor(() => expect(screen.getByText("Page 1 of 4")).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() => {
      expect(mockedGet).toHaveBeenCalledWith("/permissions", expect.objectContaining({ page: 1 }));
    });
  });
});

describe("PermissionsScreen filtering", () => {
  it("does not claim to have searched the whole catalogue", async () => {
    renderWithClient(<PermissionsScreen />);
    await waitFor(() => {
      // The API exposes no search parameter, so the copy must be honest about the scope.
      expect(screen.getByText(/catalogue holds 169 permissions/)).toBeInTheDocument();
    });
  });

  it("narrows the visible rows by code, name or description", async () => {
    const user = userEvent.setup();
    renderWithClient(<PermissionsScreen />);
    await waitFor(() => expect(screen.getByText("USER_CREATE")).toBeInTheDocument());

    await user.type(screen.getByLabelText("Search"), "audit");

    await waitFor(() => {
      expect(screen.getByText("AUDIT_VIEW")).toBeInTheDocument();
      expect(screen.queryByText("USER_CREATE")).not.toBeInTheDocument();
    });
  });

  it("filters by status", async () => {
    const user = userEvent.setup();
    renderWithClient(<PermissionsScreen />);
    await waitFor(() => expect(screen.getByText("USER_CREATE")).toBeInTheDocument());

    await user.selectOptions(screen.getByLabelText("Status"), "INACTIVE");

    await waitFor(() => {
      expect(screen.getByText("AUDIT_VIEW")).toBeInTheDocument();
      expect(screen.queryByText("USER_CREATE")).not.toBeInTheDocument();
    });
  });

  it("clear filters restores every row", async () => {
    const user = userEvent.setup();
    renderWithClient(<PermissionsScreen />);
    await waitFor(() => expect(screen.getByText("USER_CREATE")).toBeInTheDocument());

    await user.type(screen.getByLabelText("Search"), "audit");
    await waitFor(() => expect(screen.queryByText("USER_CREATE")).not.toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "Clear filters" }));

    await waitFor(() => expect(screen.getByText("USER_CREATE")).toBeInTheDocument());
  });

  it("clearing a filter returns to the first page", async () => {
    const user = userEvent.setup();
    mockedGet.mockImplementation((_url: string, params?: Record<string, unknown>) =>
      Promise.resolve(paged(Number(params?.page ?? 0), 50, 169)),
    );
    renderWithClient(<PermissionsScreen />);

    await waitFor(() => expect(screen.getByText("Page 1 of 4")).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(screen.getByText("Page 2 of 4")).toBeInTheDocument());

    // Page 4 of the unfiltered list says nothing about the filtered list.
    await user.type(screen.getByLabelText("Search"), "audit");

    await waitFor(() => {
      expect(mockedGet).toHaveBeenCalledWith("/permissions", expect.objectContaining({ page: 0 }));
    });
  });
});
