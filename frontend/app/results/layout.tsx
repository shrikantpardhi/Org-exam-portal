import type { ReactNode } from "react";

import { StudentShell } from "@/components/shell/student-shell";

/** Result pages belong to the student workspace ({@code specs/15-P6 §39}). */
export default function ResultsLayout({ children }: { children: ReactNode }) {
  return <StudentShell>{children}</StudentShell>;
}
