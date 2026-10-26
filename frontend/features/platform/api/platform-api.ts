/**
 * Platform (SUPER_ADMIN) API layer (specs/05-API-Contract-V1.5.md §72.2-§72.9).
 *
 * Only the frozen platform endpoints are called. Lists use server pagination and filtering and
 * never load an unbounded dataset.
 */
import { z } from "zod";

import { apiGet, apiPost } from "@/lib/api-client";
import {
  adminSummarySchema,
  organizationDetailSchema,
  organizationSummarySchema,
  platformStatusSchema,
  platformSystemSchema,
  providersResponseSchema,
  type AdminSummary,
  type CreateAdmin,
  type CreateOrganization,
  type OrganizationDetail,
  type OrganizationSummary,
  type PlatformPage,
  type PlatformStatus,
  type PlatformSystem,
  type ProvidersResponse,
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

export async function createOrganizationAdmin(id: string, body: CreateAdmin): Promise<AdminSummary> {
  const result = await apiPost<unknown>("/platform/organizations/" + id + "/admins", body);
  return adminSummarySchema.parse(result.data);
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
