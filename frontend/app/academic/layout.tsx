import type { ReactNode } from "react";

import { StaffAreaLayout } from "@/components/shell/staff-area-layout";

/** Academic masters live in the staff workspace ({@code specs/11-P2-Academic-V1.5.md}). */
export default function AcademicLayout({ children }: { children: ReactNode }) {
  return (
    <StaffAreaLayout
      permissions={["EXAM_VIEW", "SUBJECT_VIEW", "CHAPTER_VIEW", "TOPIC_VIEW", "SUB_TOPIC_VIEW"]}
    >
      {children}
    </StaffAreaLayout>
  );
}
