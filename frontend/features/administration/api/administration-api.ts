/**
 * Administration API layer ({@code specs/20-P11-Administration-V1.5.md §23, §122-§130}).
 *
 * <p>Only frozen endpoints are called. Lists use the server's pagination, filtering and allow-listed
 * sorting; the browser never loads an unbounded dataset ({@code §65}).
 */
import { z } from "zod";

import { apiDelete, apiGet, apiPost, apiPut } from "@/lib/api-client";
import {
  adminDashboardSchema,
  auditLogSchema,
  businessRuleSchema,
  lookupSchema,
  organizationSettingSchema,
  permissionSummarySchema,
  roleSummarySchema,
  systemHealthSchema,
  userDetailSchema,
  userSummarySchema,
  type AdminPage,
  type AdminDashboard,
  type AuditLog,
  type BusinessRule,
  type CreateUser,
  type Lookup,
  type OrganizationSetting,
  type PermissionSummary,
  type RoleSummary,
  type SystemHealth,
  type UpdateUser,
  type UserDetail,
  type UserSummary,
} from "@/features/administration/schemas/administration";

async function loadPage<T extends z.ZodTypeAny>(
  path: string,
  schema: T,
  params: Record<string, unknown>,
): Promise<AdminPage<z.infer<T>>> {
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

export interface UserFilters {
  status?: string;
  roleId?: string;
  search?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export function listUsers(filters: UserFilters = {}): Promise<AdminPage<UserSummary>> {
  return loadPage("/users", userSummarySchema, {
    ...(filters.status ? { status: filters.status } : {}),
    ...(filters.roleId ? { roleId: filters.roleId } : {}),
    ...(filters.search ? { search: filters.search } : {}),
    ...(filters.sort ? { sort: filters.sort } : {}),
    page: filters.page ?? 0,
    size: filters.size ?? 20,
  });
}

export async function getUser(userId: string): Promise<UserDetail> {
  const result = await apiGet<unknown>(`/users/${userId}`);
  return userDetailSchema.parse(result.data);
}

export async function archiveUser(userId: string): Promise<void> {
  await apiDelete(`/users/${userId}`);
}

export async function createUser(body: CreateUser): Promise<UserDetail> {
  const result = await apiPost<unknown>("/users", body);
  return userDetailSchema.parse(result.data);
}

export async function updateUser(userId: string, body: UpdateUser): Promise<UserDetail> {
  const result = await apiPut<unknown>(`/users/${userId}`, body);
  return userDetailSchema.parse(result.data);
}

export function listRoles(sort?: string): Promise<AdminPage<RoleSummary>> {
  return loadPage("/roles", roleSummarySchema, {
    ...(sort ? { sort } : {}),
    page: 0,
    size: 100,
  });
}

export async function replaceRolePermissions(
  roleId: string,
  permissionIds: string[],
): Promise<PermissionSummary[]> {
  const result = await apiPut<unknown>(`/roles/${roleId}/permissions`, { permissionIds });
  return z.array(permissionSummarySchema).parse(result.data);
}

export async function listRolePermissions(roleId: string): Promise<PermissionSummary[]> {
  const result = await apiGet<unknown>(`/roles/${roleId}/permissions`);
  return z.array(permissionSummarySchema).parse(result.data);
}

export function listPermissions(page = 0, size = 200, sort?: string): Promise<AdminPage<PermissionSummary>> {
  return loadPage("/permissions", permissionSummarySchema, {
    ...(sort ? { sort } : {}),
    page,
    size,
  });
}

export async function getOrganizationSettings(): Promise<OrganizationSetting> {
  const result = await apiGet<unknown>("/organization-settings");
  return organizationSettingSchema.parse(result.data);
}

export async function updateOrganizationSettings(
  configuration: Record<string, unknown>,
): Promise<OrganizationSetting> {
  const result = await apiPut<unknown>("/organization-settings", { configuration });
  return organizationSettingSchema.parse(result.data);
}

export async function listBusinessRules(status?: string): Promise<BusinessRule[]> {
  const result = await apiGet<unknown>("/business-rules", status ? { status } : undefined);
  return z.array(businessRuleSchema).parse(result.data);
}

export async function createBusinessRule(body: {
  code: string;
  configuration: Record<string, unknown>;
}): Promise<BusinessRule> {
  const result = await apiPost<unknown>("/business-rules", body);
  return businessRuleSchema.parse(result.data);
}

export async function updateBusinessRule(
  ruleId: string,
  configuration: Record<string, unknown>,
): Promise<BusinessRule> {
  const result = await apiPut<unknown>(`/business-rules/${ruleId}`, { configuration });
  return businessRuleSchema.parse(result.data);
}

export async function listLookups(filters: { category?: string; status?: string } = {}): Promise<Lookup[]> {
  const result = await apiGet<unknown>("/lookups", {
    ...(filters.category ? { category: filters.category } : {}),
    ...(filters.status ? { status: filters.status } : {}),
  });
  return z.array(lookupSchema).parse(result.data);
}

export async function createLookup(body: Record<string, unknown>): Promise<Lookup> {
  const result = await apiPost<unknown>("/lookups", body);
  return lookupSchema.parse(result.data);
}

export async function updateLookup(lookupId: string, body: Record<string, unknown>): Promise<Lookup> {
  const result = await apiPut<unknown>(`/lookups/${lookupId}`, body);
  return lookupSchema.parse(result.data);
}

export async function deleteLookup(lookupId: string): Promise<void> {
  await apiDelete(`/lookups/${lookupId}`);
}

export interface AuditFilters {
  userId?: string;
  entityName?: string;
  entityId?: string;
  action?: string;
  from?: string;
  to?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export function searchAudit(filters: AuditFilters = {}): Promise<AdminPage<AuditLog>> {
  return loadPage("/audit-logs", auditLogSchema, {
    ...(filters.userId ? { userId: filters.userId } : {}),
    ...(filters.entityName ? { entityName: filters.entityName } : {}),
    ...(filters.entityId ? { entityId: filters.entityId } : {}),
    ...(filters.action ? { action: filters.action } : {}),
    ...(filters.from ? { from: filters.from } : {}),
    ...(filters.to ? { to: filters.to } : {}),
    ...(filters.sort ? { sort: filters.sort } : {}),
    page: filters.page ?? 0,
    size: filters.size ?? 20,
  });
}

export async function getAdminDashboard(): Promise<AdminDashboard> {
  const result = await apiGet<unknown>("/admin/dashboard");
  return adminDashboardSchema.parse(result.data);
}

export async function getSystemHealth(): Promise<SystemHealth> {
  const result = await apiGet<unknown>("/admin/system-health");
  return systemHealthSchema.parse(result.data);
}
