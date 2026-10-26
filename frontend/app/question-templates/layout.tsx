import type { ReactNode } from "react";

import { StaffAreaLayout } from "@/components/shell/staff-area-layout";

/** Question templates are authoring tools ({@code specs/20-P11 §63}). */
export default function QuestionTemplatesLayout({ children }: { children: ReactNode }) {
  return (
    <StaffAreaLayout permissions={["QUESTION_TEMPLATE_VIEW", "QUESTION_VIEW"]}>
      {children}
    </StaffAreaLayout>
  );
}
