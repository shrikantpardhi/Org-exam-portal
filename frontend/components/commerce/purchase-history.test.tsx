import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PurchaseHistory } from "@/components/commerce/purchase-history";
import type { Purchase } from "@/features/commerce/schemas/commerce";

const purchases: Purchase[] = [
  {
    id: "purchase1",
    subscriptionPlanId: "p1",
    accessPackageId: "pkg1",
    planName: "Monthly access",
    couponId: null,
    purchaseDate: "2026-10-22T10:00:00Z",
    amount: 1000,
    paymentStatus: "SUCCESS",
  },
  {
    id: "purchase2",
    subscriptionPlanId: "p2",
    accessPackageId: "pkg1",
    planName: "Yearly access",
    couponId: "c1",
    purchaseDate: "2026-10-20T10:00:00Z",
    amount: 9000,
    paymentStatus: "PENDING",
  },
];

describe("PurchaseHistory", () => {
  it("shows the date, plan, amount and payment status of each purchase", () => {
    render(<PurchaseHistory purchases={purchases} />);

    const table = screen.getByRole("table", { name: "Purchase history" });
    const monthly = within(table).getByText("Monthly access").closest("tr");
    expect(monthly).not.toBeNull();
    expect(monthly).toHaveTextContent("1,000.00");
    expect(monthly).toHaveTextContent("Payment successful");
    expect(within(monthly as HTMLElement).getByRole("link", { name: "View" })).toHaveAttribute(
      "href",
      "/student/purchases/purchase1",
    );

    const yearly = within(table).getByText("Yearly access").closest("tr");
    expect(yearly).not.toBeNull();
    expect(yearly).toHaveTextContent("9,000.00");
    expect(yearly).toHaveTextContent("Payment pending");
  });
});
