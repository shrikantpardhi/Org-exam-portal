"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  archiveUser,
  createBusinessRule,
  createLookup,
  createUser,
  deleteLookup,
  getAdminDashboard,
  getOrganizationSettings,
  getSystemHealth,
  getUser,
  listBusinessRules,
  listLookups,
  listPermissions,
  listRolePermissions,
  listRoles,
  listUsers,
  replaceRolePermissions,
  searchAudit,
  updateBusinessRule,
  updateLookup,
  updateOrganizationSettings,
  updateUser,
  type AuditFilters,
  type UserFilters,
} from "@/features/administration/api/administration-api";
import {
  getOrganizationProfile,
  updateOrganizationProfile,
  type OrganizationProfile,
} from "@/features/administration/api/organization-config-api";
import { adminKeys } from "@/features/administration/query-keys";
import type { CreateUser, UpdateUser } from "@/features/administration/schemas/administration";
import type { ApiClientError } from "@/lib/errors";

export function useAdminDashboard() {
  return useQuery({ queryKey: adminKeys.dashboard(), queryFn: getAdminDashboard });
}

export function useUsers(filters: UserFilters, enabled = true) {
  return useQuery({
    queryKey: adminKeys.users(filters),
    queryFn: () => listUsers(filters),
    enabled,
  });
}

export function useUser(userId: string) {
  return useQuery({
    queryKey: adminKeys.user(userId),
    queryFn: () => getUser(userId),
    enabled: userId.length > 0,
  });
}

export function useRoles(enabled = true, sort?: string) {
  return useQuery({
    queryKey: adminKeys.roles(sort),
    queryFn: () => listRoles(sort),
    enabled,
  });
}

export function useRolePermissions(roleId: string, enabled = true) {
  return useQuery({
    queryKey: adminKeys.rolePermissions(roleId),
    queryFn: () => listRolePermissions(roleId),
    enabled: enabled && roleId.length > 0,
  });
}

export function usePermissions(page = 0, sort?: string) {
  return useQuery({
    queryKey: adminKeys.permissions(page, sort),
    queryFn: () => listPermissions(page, undefined, sort),
  });
}

export function useSettings() {
  return useQuery({ queryKey: adminKeys.settings(), queryFn: getOrganizationSettings });
}

export function useUpdateSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (configuration: Record<string, unknown>) => updateOrganizationSettings(configuration),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.settings() });
    },
  });
}

export function useBusinessRules(status?: string, enabled = true) {
  return useQuery({
    queryKey: adminKeys.businessRules(status),
    queryFn: () => listBusinessRules(status),
    enabled,
  });
}

export function useCreateBusinessRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { code: string; configuration: Record<string, unknown> }) =>
      createBusinessRule(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["administration", "business-rules"] });
    },
  });
}

export function useUpdateBusinessRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ ruleId, configuration }: { ruleId: string; configuration: Record<string, unknown> }) =>
      updateBusinessRule(ruleId, configuration),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["administration", "business-rules"] });
    },
  });
}

export function useLookups(filters: { category?: string; status?: string }, enabled = true) {
  return useQuery({
    queryKey: adminKeys.lookups(filters),
    queryFn: () => listLookups(filters),
    enabled,
  });
}

export function useCreateLookup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => createLookup(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["administration", "lookups"] });
    },
  });
}

export function useUpdateLookup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ lookupId, body }: { lookupId: string; body: Record<string, unknown> }) =>
      updateLookup(lookupId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["administration", "lookups"] });
    },
  });
}

export function useDeleteLookup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (lookupId: string) => deleteLookup(lookupId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["administration", "lookups"] });
    },
  });
}

export function useAuditLogs(filters: AuditFilters, enabled = true) {
  return useQuery({
    queryKey: adminKeys.audit(filters),
    queryFn: () => searchAudit(filters),
    enabled,
  });
}

export function useSystemHealth() {
  return useQuery({ queryKey: adminKeys.health(), queryFn: getSystemHealth });
}

export function useArchiveUser() {
  const queryClient = useQueryClient();
  return useMutation<void, ApiClientError, string>({
    mutationFn: (userId) => archiveUser(userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["administration", "users"] });
    },
  });
}

export function useCreateUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateUser) => createUser(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["administration", "users"] });
    },
  });
}

export function useUpdateUser(userId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateUser) => updateUser(userId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["administration", "user", userId] });
      void queryClient.invalidateQueries({ queryKey: ["administration", "users"] });
    },
  });
}

export function useReplaceRolePermissions(roleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (permissionIds: string[]) => replaceRolePermissions(roleId, permissionIds),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["administration", "roles"] });
      void queryClient.invalidateQueries({ queryKey: ["auth"] });
    },
  });
}

/** The caller's editable organization profile ({@code specs/20-P11} §8; ADR-0051). */
export function useOrganizationProfile() {
  return useQuery({
    queryKey: adminKeys.organizationProfile(),
    queryFn: () => getOrganizationProfile(),
  });
}

/** Saves the organization profile and refreshes the cached copy. */
export function useUpdateOrganizationProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: Partial<OrganizationProfile>) => updateOrganizationProfile(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.organizationProfile() });
      void queryClient.invalidateQueries({ queryKey: adminKeys.settings() });
    },
  });
}
