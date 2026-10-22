import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

import { apiGet } from "@/lib/api-client";
import { AuditScreen } from "@/features/administration/components/audit-screen";

const mockedGet = vi.mocked(apiGet);

const entry = {
  id: "audit1",
  userId: "11111111-1111-1111-1111-111111111111",
  entityName: "User",
  entityId: "22222222-2222-2222-2222-222222222222",
  action: "CREATE",
  remarks: "Created user",
  createdOn: "2026-10-22T10:00:00Z",
};

function renderAudit() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuditScreen />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedGet.mockResolvedValue({
    message: "Audit trail retrieved",
    data: [entry],
    pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
  } as never);
});

describe("audit explorer filtering", () => {
  it("passes the actor, entity, action and date range to the frozen endpoint", async () => {
    const user = userEvent.setup();
    renderAudit();

    await screen.findByText("Created user");
    await user.type(screen.getByLabelText("Actor user id"), "11111111-1111-1111-1111-111111111111");
    await user.type(screen.getByLabelText("Entity"), "User");
    await user.type(screen.getByLabelText("Action"), "CREATE");
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-10-01T10:00" } });
    await user.click(screen.getByRole("button", { name: "Apply filters" }));

    const filteredCall = mockedGet.mock.calls.find(
      (call) => (call[1] as { userId?: string } | undefined)?.userId,
    );
    expect(filteredCall?.[0]).toBe("/audit-logs");
    expect(filteredCall?.[1]).toMatchObject({
      userId: "11111111-1111-1111-1111-111111111111",
      entityName: "User",
      action: "CREATE",
      page: 0,
    });
    expect(typeof (filteredCall?.[1] as { from?: string }).from).toBe("string");
  });

  it("opens the read-only audit drawer for one event", async () => {
    const user = userEvent.setup();
    renderAudit();

    await screen.findByText("Created user");
    await user.click(screen.getByRole("button", { name: "View" }));

    const drawer = await screen.findByRole("dialog", { name: "Audit event" });
    expect(drawer).toHaveTextContent("Created user");
    expect(drawer).toHaveTextContent("CREATE");
    expect(drawer).toHaveTextContent("Audit event");
    expect(screen.queryByRole("button", { name: /delete/i })).toBeNull();
  });

  it("rejects a malformed actor id without calling the API", async () => {
    const user = userEvent.setup();
    renderAudit();

    await screen.findByText("Created user");
    const initialCalls = mockedGet.mock.calls.length;
    await user.type(screen.getByLabelText("Actor user id"), "not-a-uuid");
    await user.click(screen.getByRole("button", { name: "Apply filters" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Enter a valid identifier");
    await waitFor(() => expect(mockedGet.mock.calls.length).toBe(initialCalls));
  });
});

describe("audit explorer server sorting", () => {
  it("sorts by an allow-listed column and keeps the active filters", async () => {
    const user = userEvent.setup();
    renderAudit();

    await screen.findByText("Created user");
    await user.type(screen.getByLabelText("Action"), "CREATE");
    await user.click(screen.getByRole("button", { name: "Apply filters" }));

    await waitFor(() =>
      expect(
        mockedGet.mock.calls.some((call) => (call[1] as { action?: string })?.action === "CREATE"),
      ).toBe(true),
    );

    // The Timestamp header is allow-listed as createdOn; clicking it sorts server-side.
    await user.click(screen.getByRole("button", { name: "Timestamp" }));

    await waitFor(() => {
      const sorted = mockedGet.mock.calls.find(
        (call) => (call[1] as { sort?: string } | undefined)?.sort === "createdOn,asc",
      );
      expect(sorted?.[0]).toBe("/audit-logs");
      // Sorting must not drop the filter the operator already applied.
      expect(sorted?.[1]).toMatchObject({ action: "CREATE", page: 0 });
    });
  });

  it("cycles the sort direction on the same column", async () => {
    const user = userEvent.setup();
    renderAudit();

    await screen.findByText("Created user");
    await user.click(screen.getByRole("button", { name: "Action" }));
    expect(
      screen.getByRole("columnheader", { name: /Action/ }),
    ).toHaveAttribute("aria-sort", "ascending");

    await user.click(screen.getByRole("button", { name: "Action" }));
    expect(
      screen.getByRole("columnheader", { name: /Action/ }),
    ).toHaveAttribute("aria-sort", "descending");

    await waitFor(() =>
      expect(
        mockedGet.mock.calls.some((call) => (call[1] as { sort?: string })?.sort === "action,desc"),
      ).toBe(true),
    );
  });

  it("never offers sorting on a column the endpoint does not allow", async () => {
    renderAudit();

    await screen.findByText("Created user");
    // Remarks is rendered as a plain header, not a sort button.
    expect(screen.queryByRole("button", { name: "Remarks" })).toBeNull();
    expect(screen.getByRole("columnheader", { name: "Remarks" })).not.toHaveAttribute("aria-sort");
  });
});
