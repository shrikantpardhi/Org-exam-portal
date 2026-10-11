"use client";

import { QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";

import { AuthProvider } from "@/lib/auth/auth-context";
import { createQueryClient } from "@/lib/query-client";

/**
 * Application providers.
 *
 * The query client is created once per browser session so that component remounts do not discard
 * the cache. The authentication provider sits inside it so it can drive its own queries.
 */
export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(() => createQueryClient());

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  );
}
