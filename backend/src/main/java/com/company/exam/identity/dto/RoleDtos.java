package com.company.exam.identity.dto;

import com.company.exam.common.enums.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Role and permission API DTOs ({@code specs/10-P1-Identity-V1.5.md §112, §114}).
 */
public final class RoleDtos {

    private RoleDtos() {
    }

    public record RoleSummary(UUID id, String code, String name, String description, Status status) {
    }

    public record CreateRoleRequest(
            @NotBlank(message = "code is required") @Size(max = 50) String code,
            @NotBlank(message = "name is required") @Size(max = 100) String name,
            @Size(max = 2000) String description) {
    }

    public record UpdateRoleRequest(
            @Size(max = 100) String name,
            @Size(max = 2000) String description,
            Status status) {
    }

    /** {@code PUT /api/v1/roles/{id}/permissions} request body: a full replacement set. */
    public record ReplaceRolePermissionsRequest(
            @NotNull(message = "permissionIds is required") List<UUID> permissionIds) {
    }
}
