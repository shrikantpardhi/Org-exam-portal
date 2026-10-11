/**
 * Authentication domain types, re-exported from the validated schemas so there is one source of
 * truth for the shape of authentication data ({@code specs/10-P1-Identity-V1.5.md §154}).
 */
export type { AuthUser, CurrentPermissions, CurrentUser, LoginResult } from "@/schemas/auth";
