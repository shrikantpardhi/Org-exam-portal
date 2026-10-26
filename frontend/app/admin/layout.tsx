import type { ReactNode } from "react";

import { AppShell } from "@/components/shell/app-shell";

/** ADMIN route tree wrapped in the authenticated shell ({@code docs/frontend/navigation.md}). */
export default function AdminLayout({ children }: { children: ReactNode }) {
  return <AppShell role="ADMIN">{children}</AppShell>;
}
