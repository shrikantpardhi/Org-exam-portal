/**
 * Application query client.
 *
 * Retry policy (specs/09-P0-Foundation-V1.5.md §45, §46):
 * reads may retry only on a network failure, because they are idempotent; mutations never retry
 * automatically, because a duplicate command could create a duplicate business effect. Future
 * sensitive operations require explicit idempotency before any retry is enabled.
 */
import { QueryClient } from "@tanstack/react-query";

import { ApiClientError } from "@/lib/errors";

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) =>
          ApiClientError.isNetworkError(error) && failureCount < 2,
      },
      mutations: {
        retry: false,
      },
    },
  });
}
