/**
 * Frontend environment configuration.
 *
 * Values are validated once, at module load, so a misconfigured deployment fails early rather
 * than producing confusing runtime behaviour. Only `NEXT_PUBLIC_*` variables are available in
 * the browser (specs/09-P0-Foundation-V1.5.md §106).
 *
 * <b>Why the API base URL is same-origin by default.</b> `NEXT_PUBLIC_*` values are inlined into
 * the JavaScript bundle at `next build` time, so an absolute origin is frozen into the image. That
 * image then only works for the one exact origin it was built with: opening the same deployment at
 * `127.0.0.1`, at a LAN address, or behind a hostname makes every API call cross-origin, which the
 * edge's `connect-src 'self'` policy blocks. The default is therefore the relative path, which is
 * resolved by the browser against whatever origin actually served the page. The Nginx edge serves
 * the frontend and `/api/` from that same origin, so no CORS round trip is needed at all in the
 * containerised deployment (GAP-164).
 */
import { z } from "zod";

/** The versioned API prefix (specs/05-API-Contract-V1.5.md). */
export const API_VERSION_PREFIX = "/api/v1";

const envSchema = z.object({
  /**
   * The API base URL. Empty means "same origin as the page". An absolute origin is still accepted
   * for the split-origin development workflow (`npm run dev` on :3000 against a backend on :8080).
   */
  NEXT_PUBLIC_API_BASE_URL: z.string().trim().default(""),
});

/**
 * Resolves the value the API client uses as its Axios `baseURL`.
 *
 * <p>Accepts every form a deployment may legitimately carry and always returns a base that already
 * includes the version prefix, so no caller has to concatenate it:
 *
 * <ul>
 *   <li>{@code ""} — same origin, the containerised default;</li>
 *   <li>{@code "/api/v1"} — an explicit relative base;</li>
 *   <li>{@code "http://host:port"} — an absolute origin without the prefix;</li>
 *   <li>{@code "http://host:port/api/v1"} — an absolute origin with the prefix.</li>
 * </ul>
 *
 * A trailing slash never produces a doubled separator. Applying the prefix at most once means the
 * configuration cannot silently produce `/api/v1/api/v1`.
 */
export function resolveApiBaseUrl(raw: string | undefined): string {
  const trimmed = (raw ?? "").trim().replace(/\/+$/, "");
  if (trimmed === "") {
    return API_VERSION_PREFIX;
  }
  if (trimmed === API_VERSION_PREFIX || trimmed.endsWith(API_VERSION_PREFIX)) {
    return trimmed;
  }
  return `${trimmed}${API_VERSION_PREFIX}`;
}

export const env = envSchema.parse({
  NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL ?? "",
});

export type Env = z.infer<typeof envSchema>;
