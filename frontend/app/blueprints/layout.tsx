import type { ReactNode } from "react";

import { StaffAreaLayout } from "@/components/shell/staff-area-layout";

/**
 * Blueprint routes ({@code specs/14-P5 §44}). Staff surface, previously shell-less.
 */
export default function Layout({ children }: { children: ReactNode }) {
  return (
    <StaffAreaLayout
      permissions={["BLUEPRINT_VIEW"]}
    >
      {children}
    </StaffAreaLayout>
  );
}
