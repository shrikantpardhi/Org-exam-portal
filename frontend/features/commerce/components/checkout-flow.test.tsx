import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

const navigation = vi.hoisted(() => ({ push: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: navigation.push }),
}));

import { apiGet, apiPost } from "@/lib/api-client";
import { CheckoutFlow } from "@/features/commerce/components/checkout-flow";

const mockedGet = vi.mocked(apiGet);
const mockedPost = vi.mocked(apiPost);

const plan = {
  id: "p1",
  accessPackageId: "pkg1",
  code: "MONTHLY",
  name: "Monthly access",
  durationDays: 30,
  price: 1000,
  status: "ACTIVE",
};

const pkg = {
  id: "pkg1",
  code: "FULL",
  name: "Full access",
  description: "Every assessment",
  status: "ACTIVE",
};

const purchase = {
  id: "purchase1",
  subscriptionPlanId: "p1",
  accessPackageId: "pkg1",
  planName: "Monthly access",
  couponId: "c1",
  purchaseDate: "2026-10-22T10:00:00Z",
  amount: 777.77,
  paymentStatus: "PENDING",
};

const validation = {
  valid: true,
  code: "SAVE",
  subscriptionPlanId: "p1",
  listPrice: 1000,
  discount: 222.23,
  finalAmount: 777.77,
  currency: "INR",
  reason: null,
};

function renderCheckout() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <CheckoutFlow planId="p1" />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedGet.mockImplementation((url: string) => {
    if (url === "/subscription-plans/p1") {
      return Promise.resolve({ message: "Plan retrieved", data: plan } as never);
    }
    if (url === "/access-packages/pkg1") {
      return Promise.resolve({ message: "Package retrieved", data: pkg } as never);
    }
    if (url === "/subscription-plans") {
      return Promise.resolve({ message: "Plans retrieved", data: [plan] } as never);
    }
    return Promise.reject(new Error(`Unexpected GET ${url}`));
  });
  mockedPost.mockImplementation((url: string) => {
    if (url === "/coupons/validate") {
      return Promise.resolve({ message: "Coupon validated", data: validation } as never);
    }
    if (url === "/purchases") {
      return Promise.resolve({ message: "Purchase created", data: purchase } as never);
    }
    if (url === "/purchases/purchase1/payment") {
      return Promise.resolve({
        message: "Payment initiated",
        data: {
          purchaseId: "purchase1",
          paymentStatus: "PENDING",
          paymentReference: "TEST-1",
          checkoutToken: "token",
          nextAction: "REDIRECT",
        },
      } as never);
    }
    return Promise.reject(new Error(`Unexpected POST ${url}`));
  });
});

describe("checkout flow", () => {
  it("applies a coupon and displays the server's payable amount", async () => {
    const user = userEvent.setup();
    renderCheckout();

    expect(await screen.findByRole("button", { name: "Start purchase" })).toBeInTheDocument();
    expect(screen.getAllByText("1,000.00").length).toBeGreaterThan(0);

    await user.type(screen.getByLabelText("Coupon code"), "SAVE");
    await user.click(screen.getByRole("button", { name: "Apply coupon" }));

    expect(await screen.findByText(/Coupon SAVE applied/)).toBeInTheDocument();
    expect(screen.getByText("777.77 INR")).toBeInTheDocument();
    expect(screen.getByText("Payable (calculated by the server)")).toBeInTheDocument();

    expect(mockedPost).toHaveBeenCalledWith("/coupons/validate", {
      code: "SAVE",
      subscriptionPlanId: "p1",
    });
  });

  it("starts the purchase without sending an amount and initiates payment", async () => {
    const user = userEvent.setup();
    renderCheckout();

    await user.click(await screen.findByRole("button", { name: "Start purchase" }));

    await waitFor(() => {
      expect(navigation.push).toHaveBeenCalledWith("/student/purchases/purchase1");
    });

    const purchaseCall = mockedPost.mock.calls.find((call) => call[0] === "/purchases");
    expect(purchaseCall?.[1]).toEqual({ subscriptionPlanId: "p1" });
    expect(purchaseCall?.[1]).not.toHaveProperty("amount");
    expect(mockedPost).toHaveBeenCalledWith("/purchases/purchase1/payment", undefined);
  });

  it("submits the applied coupon code with the plan, still not an amount", async () => {
    const user = userEvent.setup();
    renderCheckout();

    await user.type(await screen.findByLabelText("Coupon code"), "SAVE");
    await user.click(screen.getByRole("button", { name: "Apply coupon" }));
    await screen.findByText(/Coupon SAVE applied/);

    await user.click(screen.getByRole("button", { name: "Start purchase" }));

    await waitFor(() => {
      expect(navigation.push).toHaveBeenCalledWith("/student/purchases/purchase1");
    });

    const purchaseCall = mockedPost.mock.calls.find((call) => call[0] === "/purchases");
    expect(purchaseCall?.[1]).toEqual({ subscriptionPlanId: "p1", couponCode: "SAVE" });
    expect(purchaseCall?.[1]).not.toHaveProperty("amount");
    expect(purchaseCall?.[1]).not.toHaveProperty("finalAmount");
  });
});
