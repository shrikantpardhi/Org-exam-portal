"use client";

import { useQuery } from "@tanstack/react-query";

import { apiGet } from "@/lib/api-client";
import { ApiClientError } from "@/lib/errors";
import { systemInfoSchema, type SystemInfo } from "@/schemas/system";

/**
 * Reads non-business application information from the backend to demonstrate the frontend to
 * backend boundary. Responses are validated with Zod before use.
 */
export function useSystemInfo() {
  return useQuery<SystemInfo, ApiClientError>({
    queryKey: ["system", "info"],
    queryFn: async () => {
      const result = await apiGet<unknown>("/system/info");
      return systemInfoSchema.parse(result.data);
    },
  });
}
