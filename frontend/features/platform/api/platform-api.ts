/**
 * Platform (SUPER_ADMIN) API layer (specs/05-API-Contract-V1.5.md §72.2-§72.9).
 *
 * Only the frozen platform endpoints are called. Lists use server pagination and filtering and
 * never load an unbounded dataset.
 */
import { z } from "zod";

import { apiDelete, apiGet, apiPatch, apiPost } from "@/lib/api-client";
import {
  adminListEntrySchema,
  adminSummarySchema,
  organizationDetailSchema,
  organizationSummarySchema,
  platformStatusSchema,
  platformSystemSchema,
  providersResponseSchema,
  type AdminListEntry,
  type AdminSummary,
  type CreateAdmin,
  type CreateOrganization,
  type OrganizationDetail,
  type OrganizationStatus,
  type OrganizationSummary,
  type PlatformPage,
  type PlatformStatus,
  type PlatformSystem,
  type ProvidersResponse,
  type UpdateOrganization,
} from "@/features/platform/schemas/platform";

async function loadPage<T extends z.ZodTypeAny>(
  path: string,
  schema: T,
  params: Record<string, unknown>,
): Promise<PlatformPage<z.infer<T>>> {
  const result = await apiGet<unknown>(path, params);
  const items = z.array(schema).parse(result.data);
  const pagination = result.pagination;
  return {
    items,
    page: pagination?.page ?? 0,
    size: pagination?.size ?? items.length,
    totalElements: pagination?.totalElements ?? items.length,
    totalPages: pagination?.totalPages ?? 1,
  };
}

export interface OrganizationFilters {
  status?: string;
  search?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export function listOrganizations(filters: OrganizationFilters = {}): Promise<PlatformPage<OrganizationSummary>> {
  return loadPage("/platform/organizations", organizationSummarySchema, {
    ...(filters.status ? { status: filters.status } : {}),
    ...(filters.search ? { search: filters.search } : {}),
    ...(filters.sort ? { sort: filters.sort } : {}),
    page: filters.page ?? 0,
    size: filters.size ?? 20,
  });
}

export async function createOrganization(body: CreateOrganization): Promise<OrganizationSummary> {
  const result = await apiPost<unknown>("/platform/organizations", body);
  return organizationSummarySchema.parse(result.data);
}

export async function getOrganization(id: string): Promise<OrganizationDetail> {
  const result = await apiGet<unknown>("/platform/organizations/" + id);
  return organizationDetailSchema.parse(result.data);
}

/**
 * Corrects a business organization's profile ({@code PATCH /platform/organizations/{id}}, ADR-0056).
 *
 * <p>The body is a true PATCH: an omitted field is left alone and an empty string clears it. The
 * caller therefore sends the difference it wants, not the whole form — sending every field with ""
 * for the untouched ones would erase the profile it was trying to preserve.
 *
 * <p>{@code code}, {@code status} and the primary administrator are deliberately absent from the
 * payload type: each has its own endpoint, permission and audit action, and the server refuses them
 * here. Returns the refreshed detail so the screen renders what the authority stored.
 */
export async function updateOrganization(
  id: string,
  payload: UpdateOrganization,
): Promise<OrganizationDetail> {
  const result = await apiPatch<unknown>("/platform/organizations/" + id, payload);
  return organizationDetailSchema.parse(result.data);
}

/**
 * Activates or suspends one organization ({@code PATCH /platform/organizations/{id}/status}).
 *
 * <p>The server answers with the updated detail, so the caller renders the status the authority
 * recorded rather than echoing what it asked for. INACTIVE suspends the whole tenant:
 * {@code AuthenticationService} refuses a login while the organization is not ACTIVE.
 */
export async function changeOrganizationStatus(
  id: string,
  status: OrganizationStatus,
): Promise<OrganizationDetail> {
  const result = await apiPatch<unknown>("/platform/organizations/" + id + "/status", { status });
  return organizationDetailSchema.parse(result.data);
}

export async function createOrganizationAdmin(id: string, body: CreateAdmin): Promise<AdminSummary> {
  const result = await apiPost<unknown>("/platform/organizations/" + id + "/admins", body);
  return adminSummarySchema.parse(result.data);
}

export interface AdminFilters {
  /**
   * Omitted means "currently-serving administrators", i.e. everything the server holds except
   * ARCHIVED ones. Sending ACTIVE or ARCHIVED narrows to that state only (ADR-0054).
   */
  status?: "ACTIVE" | "ARCHIVED";
  search?: string;
  sort?: string;
  page?: number;
  size?: number;
}

/** {@code GET /platform/organizations/{id}/admins} (ADR-0053). */
export function listOrganizationAdmins(
  id: string,
  filters: AdminFilters = {},
): Promise<PlatformPage<AdminListEntry>> {
  return loadPage("/platform/organizations/" + id + "/admins", adminListEntrySchema, {
    // Only append status when the operator chose a state: the server treats a missing status as
    // "not archived", which is deliberately different from status=ACTIVE.
    ...(filters.status ? { status: filters.status } : {}),
    ...(filters.search ? { search: filters.search } : {}),
    ...(filters.sort ? { sort: filters.sort } : {}),
    page: filters.page ?? 0,
    size: filters.size ?? 10,
  });
}

/** {@code PATCH /platform/organizations/{id}/admins/{userId}/roles} (ADR-0053). */
export async function updateOrganizationAdminRoles(
  id: string,
  userId: string,
  roles: string[],
): Promise<AdminListEntry> {
  const result = await apiPatch<unknown>(
    "/platform/organizations/" + id + "/admins/" + userId + "/roles",
    { roles },
  );
  return adminListEntrySchema.parse(result.data);
}

/**
 * Archives one administrator ({@code DELETE /platform/organizations/{id}/admins/{userId}}, ADR-0054).
 *
 * <p>The verb on the wire is DELETE but the effect is an archive: the row moves to ARCHIVED, keeps
 * its role history, and can be restored. It is reversible, so the screen names the action
 * "Archive" rather than implying a permanent deletion.
 *
 * <p>Server refusals (409) are meaningful and are surfaced verbatim: the primary administrator
 * cannot be archived, and neither can the last active one.
 */
export async function archiveOrganizationAdmin(id: string, userId: string): Promise<void> {
  await apiDelete("/platform/organizations/" + id + "/admins/" + userId);
}

/**
 * Un-archives one administrator ({@code POST .../admins/{userId}/restore}, ADR-0054).
 *
 * <p>Returns the restored row so the caller can show the account as ACTIVE without a refetch race.
 */
export async function restoreOrganizationAdmin(
  id: string,
  userId: string,
): Promise<AdminListEntry> {
  const result = await apiPost<unknown>(
    "/platform/organizations/" + id + "/admins/" + userId + "/restore",
    {},
  );
  return adminListEntrySchema.parse(result.data);
}

/**
 * Transfers the primary-administrator designation
 * ({@code PATCH .../primary-admin}, ADR-0054).
 *
 * <p>Idempotent when the target is already primary. Returns the refreshed organization detail so
 * the caller can rely on the server's answer instead of guessing the new designation.
 */
export async function setOrganizationPrimaryAdmin(
  id: string,
  userId: string,
): Promise<OrganizationDetail> {
  const result = await apiPatch<unknown>("/platform/organizations/" + id + "/primary-admin", {
    userId,
  });
  return organizationDetailSchema.parse(result.data);
}

export async function getProviders(): Promise<ProvidersResponse> {
  const result = await apiGet<unknown>("/platform/providers");
  return providersResponseSchema.parse(result.data);
}

export async function getPlatformStatus(): Promise<PlatformStatus> {
  const result = await apiGet<unknown>("/platform/status");
  return platformStatusSchema.parse(result.data);
}

export async function getPlatformSystem(): Promise<PlatformSystem> {
  const result = await apiGet<unknown>("/platform/system");
  return platformSystemSchema.parse(result.data);
}
