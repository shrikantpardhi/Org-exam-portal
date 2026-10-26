import type { ReactNode } from "react";

import { StudentShell } from "@/components/shell/student-shell";

/**
 * The attempt-bound result view is a student surface ({@code specs/15-P6 §95}).
 *
 * <p>It lived at the app root with no shell, so a student opening a result from a link lost the whole
 * workspace and had no way back. It sits beside /results for that reason; the URL is unchanged.
 */
export default function AttemptsLayout({ children }: { children: ReactNode }) {
  return <StudentShell>{children}</StudentShell>;
}
