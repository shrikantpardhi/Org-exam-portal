import type { ReactNode } from "react";

import { StaffAreaLayout } from "@/components/shell/staff-area-layout";

/**
 * Assessment authoring routes ({@code specs/14-P5 §40}). Staff surface, previously shell-less.
 */
export default function Layout({ children }: { children: ReactNode }) {
  return (
    <StaffAreaLayout
      permissions={["ASSESSMENT_VIEW", "ASSESSMENT_VERSION_VIEW"]}
    >
      {children}
    </StaffAreaLayout>
  );
}
