import { describe, expect, it } from "vitest";

import {
  accessGrantSchema,
  couponValidationSchema,
  createCouponFormSchema,
  createPlanFormSchema,
  purchaseSchema,
  subscriptionPlanSchema,
} from "@/features/commerce/schemas/commerce";

describe("commerce schemas", () => {
  it("parses plan prices exactly as the server sends them", () => {
    const plan = subscriptionPlanSchema.parse({
      id: "p1",
      accessPackageId: "pkg1",
      code: "MONTHLY",
      name: "Monthly",
      durationDays: 30,
      price: "1000.00",
      status: "ACTIVE",
    });

    expect(plan.price).toBe(1000);
    expect(plan.durationDays).toBe(30);
    expect(plan.status).toBe("ACTIVE");
  });

  it("keeps the server's coupon decision and final amount", () => {
    const validation = couponValidationSchema.parse({
      valid: true,
      code: "SAVE",
      subscriptionPlanId: "p1",
      listPrice: 1000,
      discount: 222.23,
      finalAmount: 777.77,
      currency: "INR",
      reason: null,
      extra: "ignored",
    });

    expect(validation.valid).toBe(true);
    expect(validation.finalAmount).toBe(777.77);
    expect(validation.currency).toBe("INR");
    expect(validation).not.toHaveProperty("extra");
  });

  it("never treats a missing payment status as successful", () => {
    const purchase = purchaseSchema.parse({
      id: "purchase1",
      subscriptionPlanId: "p1",
      accessPackageId: "pkg1",
      planName: "Monthly",
      purchaseDate: "2026-10-22T10:00:00Z",
      amount: 777.77,
    });

    expect(purchase.paymentStatus).toBe("UNKNOWN");
    expect(purchase.paymentStatus).not.toBe("SUCCESS");
  });

  it("defaults an absent access-grant active flag to false", () => {
    const grant = accessGrantSchema.parse({
      id: "g1",
      accessPackageId: "pkg1",
      packageName: "Full access",
      startDate: "2026-10-01T00:00:00Z",
      endDate: null,
      status: "ACTIVE",
    });

    expect(grant.active).toBe(false);
    expect(grant.endDate).toBeNull();
  });

  it("transforms form values for the frozen request shapes", () => {
    const coupon = createCouponFormSchema.parse({
      code: " save ",
      name: " Save 100 ",
      discountValue: "100",
      validFrom: "2026-11-01T10:00",
      validTo: "",
    });
    expect(coupon.code).toBe("save");
    expect(coupon.discountValue).toBe(100);
    expect(coupon.validFrom).toBe(new Date("2026-11-01T10:00").toISOString());
    expect(coupon.validTo).toBeNull();

    const plan = createPlanFormSchema.parse({
      accessPackageId: "pkg1",
      code: "YEAR",
      name: "Yearly",
      durationDays: "365",
      price: "9999.50",
    });
    expect(plan.durationDays).toBe(365);
    expect(plan.price).toBe(9999.5);
  });
});
