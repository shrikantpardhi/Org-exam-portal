"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  createOrganization,
  createOrganizationAdmin,
  getOrganization,
  getPlatformStatus,
  getPlatformSystem,
  getProviders,
  listOrganizations,
  type OrganizationFilters,
} from "@/features/platform/api/platform-api";
import { platformKeys } from "@/features/platform/query-keys";
import type { CreateAdmin, CreateOrganization } from "@/features/platform/schemas/platform";

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

export function useCreateOrganizationAdmin(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateAdmin) => createOrganizationAdmin(organizationId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: platformKeys.organization(organizationId) });
      void queryClient.invalidateQueries({ queryKey: platformKeys.organizations({}) });
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
