import type { ReactNode } from "react";

import { StaffAreaLayout } from "@/components/shell/staff-area-layout";

/**
 * Staff routes ({@code specs/20-P11 §63}). Staff surface, previously shell-less.
 */
export default function Layout({ children }: { children: ReactNode }) {
  return (
    <StaffAreaLayout
      permissions={["ACCESS_GRANT_MANAGE", "REPORT_OPERATIONS_READ"]}
    >
      {children}
    </StaffAreaLayout>
  );
}
