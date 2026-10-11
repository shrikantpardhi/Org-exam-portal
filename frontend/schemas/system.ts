import { z } from "zod";

/** Response shape of the infrastructure `GET /api/v1/system/info` endpoint. */
export const systemInfoSchema = z.object({
  application: z.string(),
  version: z.string(),
  environment: z.string(),
  timestamp: z.string(),
  requestId: z.string().nullish(),
});

export type SystemInfo = z.infer<typeof systemInfoSchema>;
