import type { ReactNode } from "react";

import { StaffAreaLayout } from "@/components/shell/staff-area-layout";

/**
 * Commerce finance routes ({@code specs/18-P9 §86}). Added under the app root rather than beneath
 * /admin, so without this they rendered outside both the workspace and its gate.
 */
export default function Layout({ children }: { children: ReactNode }) {
  return (
    <StaffAreaLayout
      permissions={["COUPON_VIEW", "PURCHASE_VIEW", "SUBSCRIPTION_PLAN_VIEW"]}
    >
      {children}
    </StaffAreaLayout>
  );
}
