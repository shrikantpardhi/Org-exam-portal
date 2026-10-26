import type { ReactNode } from "react";

import { AppShell } from "@/components/shell/app-shell";

/** SUPER_ADMIN route tree wrapped in the authenticated shell ({@code docs/frontend/navigation.md}). */
export default function PlatformLayout({ children }: { children: ReactNode }) {
  return <AppShell role="SUPER_ADMIN">{children}</AppShell>;
}
