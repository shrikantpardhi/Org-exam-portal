import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

import { apiGet, apiPost } from "@/lib/api-client";
import { FinancePurchaseLookup } from "@/features/commerce/components/finance-purchase-lookup";

const mockedGet = vi.mocked(apiGet);
const mockedPost = vi.mocked(apiPost);

const pkg = {
  id: "pkg1",
  code: "FULL",
  name: "Full access",
  description: "Every assessment",
  status: "ACTIVE",
};

let purchaseStatus = "SUCCESS";

function renderLookup(mode: "purchases" | "payments" | "refunds") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <FinancePurchaseLookup mode={mode} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  purchaseStatus = "SUCCESS";
  mockedGet.mockImplementation((url: string) => {
    if (url === "/purchases/p1") {
      return Promise.resolve({
        message: "Purchase retrieved",
        data: {
          id: "p1",
          subscriptionPlanId: "plan1",
          accessPackageId: "pkg1",
          planName: "Monthly access",
          couponId: null,
          purchaseDate: "2026-10-22T10:00:00Z",
          amount: 1000,
          paymentStatus: purchaseStatus,
        },
      } as never);
    }
    if (url === "/access-packages/pkg1") {
      return Promise.resolve({ message: "Package retrieved", data: pkg } as never);
    }
    return Promise.reject(new Error(`Unexpected GET ${url}`));
  });
});

describe("finance purchase workspace", () => {
  it("views a purchase and refunds it after confirmation", async () => {
    const user = userEvent.setup();
    mockedPost.mockResolvedValue({
      message: "Purchase refunded",
      data: { purchaseId: "p1", paymentStatus: "REFUNDED", refunded: true },
    } as never);

    renderLookup("refunds");

    await user.type(screen.getByLabelText("Purchase id"), "p1");
    await user.click(screen.getByRole("button", { name: "Load purchase" }));

    const refundButton = await screen.findByRole("button", { name: "Refund purchase" });
    expect(screen.getAllByText("Payment successful").length).toBeGreaterThan(0);

    await user.click(refundButton);
    purchaseStatus = "REFUNDED";
    await user.click(screen.getByRole("button", { name: "Confirm refund" }));

    expect(mockedPost).toHaveBeenCalledWith("/purchases/p1/refund", undefined);
    expect(await screen.findByText("This purchase is already refunded.")).toBeInTheDocument();
  });

  it("shows the authoritative payment status in the payments workspace", async () => {
    const user = userEvent.setup();
    renderLookup("payments");

    await user.type(screen.getByLabelText("Purchase id"), "p1");
    await user.click(screen.getByRole("button", { name: "Load purchase" }));

    expect((await screen.findAllByText("Payment successful")).length).toBeGreaterThan(0);
    expect(
      screen.getByText(/The provider detail is restricted to the purchase owner/),
    ).toBeInTheDocument();
  });

  it("does not offer a refund for a purchase that was not paid", async () => {
    purchaseStatus = "PENDING";
    const user = userEvent.setup();
    renderLookup("refunds");

    await user.type(screen.getByLabelText("Purchase id"), "p1");
    await user.click(screen.getByRole("button", { name: "Load purchase" }));

    expect(
      await screen.findByText(/Only a successful payment can be refunded/),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Refund purchase" })).toBeNull();
  });
});
