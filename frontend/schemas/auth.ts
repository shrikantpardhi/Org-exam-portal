/**
 * Authentication response schemas.
 *
 * <p>Responses are validated with Zod at the boundary so an unexpected server shape fails fast
 * instead of leaking into UI state.
 */
import { z } from "zod";

export const authUserSchema = z.object({
  id: z.string(),
  firstName: z.string().nullish(),
  lastName: z.string().nullish(),
  roles: z.array(z.string()),
});

export const loginResultSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.string(),
  expiresIn: z.number(),
  user: authUserSchema,
});

export const currentUserSchema = z.object({
  id: z.string(),
  firstName: z.string().nullish(),
  lastName: z.string().nullish(),
  email: z.string(),
  roles: z.array(z.string()),
  permissions: z.array(z.string()),
});

export const currentPermissionsSchema = z.object({
  permissions: z.array(z.string()),
});

export type AuthUser = z.infer<typeof authUserSchema>;
export type LoginResult = z.infer<typeof loginResultSchema>;
export type CurrentUser = z.infer<typeof currentUserSchema>;
export type CurrentPermissions = z.infer<typeof currentPermissionsSchema>;
