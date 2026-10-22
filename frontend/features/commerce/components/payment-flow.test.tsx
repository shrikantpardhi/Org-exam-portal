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

import { apiGet, apiPost } from "@/lib/api-client";
import { PurchaseDetailView } from "@/features/commerce/components/purchase-detail-view";

const mockedGet = vi.mocked(apiGet);
const mockedPost = vi.mocked(apiPost);

const pkg = {
  id: "pkg1",
  code: "FULL",
  name: "Full access",
  description: "Every assessment",
  status: "ACTIVE",
};

let purchaseStatus = "PENDING";
let paymentStatus = "PENDING";
let paymentDate: string | null = null;

function renderPurchase() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <PurchaseDetailView purchaseId="purchase1" />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  purchaseStatus = "PENDING";
  paymentStatus = "PENDING";
  paymentDate = null;

  mockedGet.mockImplementation((url: string) => {
    if (url === "/purchases/purchase1") {
      return Promise.resolve({
        message: "Purchase retrieved",
        data: {
          id: "purchase1",
          subscriptionPlanId: "p1",
          accessPackageId: "pkg1",
          planName: "Monthly access",
          couponId: null,
          purchaseDate: "2026-10-22T10:00:00Z",
          amount: 1000,
          paymentStatus: purchaseStatus,
        },
      } as never);
    }
    if (url === "/purchases/purchase1/payment") {
      return Promise.resolve({
        message: "Payment retrieved",
        data: {
          purchaseId: "purchase1",
          paymentStatus,
          paymentReference: "TEST-1",
          paymentMethod: "TEST_CARD",
          paymentDate,
        },
      } as never);
    }
    if (url === "/access-packages/pkg1") {
      return Promise.resolve({ message: "Package retrieved", data: pkg } as never);
    }
    return Promise.reject(new Error(`Unexpected GET ${url}`));
  });
});

describe("payment pending to success transition", () => {
  it("follows the server status when it changes from pending to successful", async () => {
    const user = userEvent.setup();
    renderPurchase();

    expect((await screen.findAllByText("Payment pending")).length).toBeGreaterThan(0);
    expect(await screen.findByText("Reference: TEST-1")).toBeInTheDocument();

    // The provider confirms asynchronously; only the server changes the state.
    purchaseStatus = "SUCCESS";
    paymentStatus = "SUCCESS";
    paymentDate = "2026-10-22T10:05:00Z";

    await user.click(screen.getByRole("button", { name: "Check payment status" }));

    expect((await screen.findAllByText("Payment successful")).length).toBeGreaterThan(0);
    expect(
      screen.getByText("Payment confirmed. Your access has been applied by the server."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View my access" })).toHaveAttribute(
      "href",
      "/student/access",
    );

    // The client never mutates the payment state.
    expect(mockedPost).not.toHaveBeenCalled();
  });

  it("offers a retry while the payment is still pending", async () => {
    const user = userEvent.setup();
    renderPurchase();

    const payButton = await screen.findByRole("button", { name: "Pay now" });
    mockedPost.mockResolvedValue({
      message: "Payment initiated",
      data: {
        purchaseId: "purchase1",
        paymentStatus: "PENDING",
        paymentReference: "TEST-1",
        checkoutToken: "token",
        nextAction: "REDIRECT",
      },
    } as never);

    await user.click(payButton);

    expect(mockedPost).toHaveBeenCalledWith("/purchases/purchase1/payment", undefined);
  });
});
