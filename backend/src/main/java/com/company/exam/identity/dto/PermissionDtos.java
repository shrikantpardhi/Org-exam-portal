package com.company.exam.identity.dto;

import com.company.exam.common.enums.Status;
import java.util.UUID;

/** Permission catalog DTO ({@code specs/10-P1-Identity-V1.5.md §112}). */
public final class PermissionDtos {

    private PermissionDtos() {
    }

    public record PermissionSummary(UUID id, String code, String name, String description, Status status) {
    }
}
