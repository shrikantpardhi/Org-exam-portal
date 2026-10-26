import type { ReactNode } from "react";

import { RoleShell } from "@/components/shell/role-shell";

/** Account settings are reachable by every role, so the layout follows the signed-in role. */
export default function AccountLayout({ children }: { children: ReactNode }) {
  return <RoleShell>{children}</RoleShell>;
}
