package com.company.exam.identity.dto;

import java.util.List;
import java.util.UUID;

/**
 * Current-user DTOs ({@code specs/10-P1-Identity-V1.5.md §32, §33}).
 */
public final class CurrentUserDtos {

    private CurrentUserDtos() {
    }

    public record CurrentUserResponse(
            UUID id,
            String firstName,
            String lastName,
            String email,
            List<String> roles,
            List<String> permissions,
            boolean mustChangePassword) {
    }

    public record CurrentPermissionsResponse(List<String> permissions) {
    }
}
