"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  archiveOrganizationAdmin,
  changeOrganizationStatus,
  createOrganization,
  createOrganizationAdmin,
  getOrganization,
  getPlatformStatus,
  getPlatformSystem,
  getProviders,
  listOrganizationAdmins,
  listOrganizations,
  restoreOrganizationAdmin,
  setOrganizationPrimaryAdmin,
  updateOrganization,
  updateOrganizationAdminRoles,
  type AdminFilters,
  type OrganizationFilters,
} from "@/features/platform/api/platform-api";
import { platformKeys } from "@/features/platform/query-keys";
import type {
  CreateAdmin,
  CreateOrganization,
  OrganizationStatus,
  UpdateOrganization,
} from "@/features/platform/schemas/platform";

export function useOrganizations(filters: OrganizationFilters, enabled = true) {
  return useQuery({
    queryKey: platformKeys.organizations(filters),
    queryFn: () => listOrganizations(filters),
    enabled,
  });
}

export function useOrganization(id: string) {
  return useQuery({
    queryKey: platformKeys.organization(id),
    queryFn: () => getOrganization(id),
    enabled: id.length > 0,
  });
}

export function useCreateOrganization() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateOrganization) => createOrganization(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: platformKeys.all() });
    },
  });
}

/**
 * Invalidates every cached view a write to one organization's administrators can affect.
 *
 * <p>The prefix {@code ["platform", "organization", id]} covers the detail (adminCount,
 * primaryAdminUserId, primaryAdminEmail) and every admin list under every filter, including the one
 * the caller is not currently looking at. A targeted list key would leave a stale Archived tab
 * behind after a restore.
 */
function invalidateOrganization(queryClient: ReturnType<typeof useQueryClient>, organizationId: string) {
  void queryClient.invalidateQueries({
    queryKey: platformKeys.organization(organizationId),
  });
}

export function useCreateOrganizationAdmin(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateAdmin) => createOrganizationAdmin(organizationId, body),
    onSuccess: () => {
      invalidateOrganization(queryClient, organizationId);
      void queryClient.invalidateQueries({ queryKey: platformKeys.organizations({}) });
    },
  });
}

export function useOrganizationAdmins(organizationId: string, filters: AdminFilters) {
  return useQuery({
    queryKey: platformKeys.organizationAdmins(organizationId, filters),
    queryFn: () => listOrganizationAdmins(organizationId, filters),
    enabled: organizationId.length > 0,
  });
}

/**
 * Replaces an administrator's non-administrator roles.
 *
 * <p>Invalidates the admin list and the organization detail together: the latter carries
 * {@code adminCount} and the primary administrator, both of which the roles call can affect.
 */
export function useUpdateOrganizationAdminRoles(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { userId: string; roles: string[] }) =>
      updateOrganizationAdminRoles(organizationId, input.userId, input.roles),
    onSuccess: () => {
      invalidateOrganization(queryClient, organizationId);
    },
  });
}

/**
 * Transfers the primary-administrator designation (ADR-0054).
 *
 * <p>Both segments are invalidated: the detail now reports a different {@code primaryAdminEmail},
 * and every admin row's {@code isPrimary} flag moved, so the list has to be refetched as well.
 */
export function useSetOrganizationPrimaryAdmin(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => setOrganizationPrimaryAdmin(organizationId, userId),
    onSuccess: () => {
      invalidateOrganization(queryClient, organizationId);
    },
  });
}

/**
 * Archives an administrator (ADR-0054).
 *
 * <p>Archiving is reversible and keeps role history, so the archive and restore hooks share one
 * invalidation: the row's {@code status} changed, and the organization's {@code adminCount} may
 * have moved with it.
 */
export function useArchiveOrganizationAdmin(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => archiveOrganizationAdmin(organizationId, userId),
    onSuccess: () => {
      invalidateOrganization(queryClient, organizationId);
    },
  });
}

/** Un-archives an administrator (ADR-0054). The mirror of {@link useArchiveOrganizationAdmin}. */
export function useRestoreOrganizationAdmin(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => restoreOrganizationAdmin(organizationId, userId),
    onSuccess: () => {
      invalidateOrganization(queryClient, organizationId);
    },
  });
}

/**
 * The key prefix of every organizations list, whatever filter it was fetched with.
 *
 * <p>Not {@code platformKeys.organizations({})}: that is the key of one exact filter object, and
 * TanStack matches keys by deep equality, so invalidating it would refresh only the unfiltered view
 * and leave an Active- or Archived-filtered list showing the old status.
 */
const ORGANIZATIONS_LIST_PREFIX = ["platform", "organizations"] as const;

/**
 * Activates or suspends an organization ({@code PATCH .../status}).
 *
 * <p>Both cached views are refreshed: the detail carries the status badge and the warning copy, and
 * every organizations list row shows the status pill, so a lifecycle change that only refreshed one
 * of them would leave the other stating the opposite.
 */
export function useChangeOrganizationStatus(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (status: OrganizationStatus) => changeOrganizationStatus(organizationId, status),
    onSuccess: () => {
      invalidateOrganization(queryClient, organizationId);
      void queryClient.invalidateQueries({ queryKey: ORGANIZATIONS_LIST_PREFIX });
    },
  });
}

/**
 * Corrects an organization's profile ({@code PATCH .../{id}}, ADR-0056).
 *
 * <p>Both cached views are refreshed: the detail carries every profile field the operator just
 * edited, and the organizations list shows the name, which this endpoint can change. The list is
 * invalidated by its literal prefix, not by one filter object, so an Active- or Archived-filtered
 * page is refetched too.
 */
export function useUpdateOrganization(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateOrganization) => updateOrganization(organizationId, payload),
    onSuccess: () => {
      invalidateOrganization(queryClient, organizationId);
      void queryClient.invalidateQueries({ queryKey: ORGANIZATIONS_LIST_PREFIX });
    },
  });
}

export function useProviders() {
  return useQuery({ queryKey: platformKeys.providers(), queryFn: getProviders });
}

export function usePlatformStatus() {
  return useQuery({ queryKey: platformKeys.status(), queryFn: getPlatformStatus });
}

export function usePlatformSystem() {
  return useQuery({ queryKey: platformKeys.system(), queryFn: getPlatformSystem });
}
