/**
 * Frontend environment configuration.
 *
 * Values are validated once, at module load, so a misconfigured deployment fails early rather
 * than producing confusing runtime behaviour. Only `NEXT_PUBLIC_*` variables are available in
 * the browser (specs/09-P0-Foundation-V1.5.md §106).
 */
import { z } from "zod";

const envSchema = z.object({
  NEXT_PUBLIC_API_BASE_URL: z.string().min(1).default("http://localhost:8080"),
});

export const env = envSchema.parse({
  NEXT_PUBLIC_API_BASE_URL:
    process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080",
});

export type Env = z.infer<typeof envSchema>;
