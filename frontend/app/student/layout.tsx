import type { ReactNode } from "react";

import { StudentShell } from "@/components/shell/student-shell";

/** STUDENT route tree wrapped in the student workspace shell. */
export default function StudentLayout({ children }: { children: ReactNode }) {
  return <StudentShell>{children}</StudentShell>;
}
