import type { ReactNode } from "react";

import { StaffAreaLayout } from "@/components/shell/staff-area-layout";

/**
 * Assessment-collection routes ({@code specs/18-P9 §77}). Staff surface, previously shell-less.
 */
export default function Layout({ children }: { children: ReactNode }) {
  return (
    <StaffAreaLayout
      permissions={["ASSESSMENT_COLLECTION_VIEW"]}
    >
      {children}
    </StaffAreaLayout>
  );
}
