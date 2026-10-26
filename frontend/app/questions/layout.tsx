import type { ReactNode } from "react";

import { StaffAreaLayout } from "@/components/shell/staff-area-layout";

/**
 * Question authoring routes ({@code specs/13-P4 §31}). These are staff surfaces that lived outside
 * any shell; wrapping them here gives them the admin workspace without moving a URL.
 */
export default function Layout({ children }: { children: ReactNode }) {
  return (
    <StaffAreaLayout
      permissions={["QUESTION_VIEW", "QUESTION_CREATE", "QUESTION_UPDATE", "QUESTION_REVIEW"]}
    >
      {children}
    </StaffAreaLayout>
  );
}
