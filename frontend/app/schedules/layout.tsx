import type { ReactNode } from "react";

import { StaffAreaLayout } from "@/components/shell/staff-area-layout";

/**
 * Assessment schedule routes ({@code specs/14-P5 §51}). Staff surface, previously shell-less.
 */
export default function Layout({ children }: { children: ReactNode }) {
  return (
    <StaffAreaLayout
      permissions={["ASSESSMENT_SCHEDULE_VIEW"]}
    >
      {children}
    </StaffAreaLayout>
  );
}
