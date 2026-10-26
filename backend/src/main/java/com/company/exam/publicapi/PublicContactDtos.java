package com.company.exam.publicapi;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public landing-page contact form DTOs ({@code specs/05 §73.2}; ADR-0037). */
public final class PublicContactDtos {

    private PublicContactDtos() {
    }

    /** {@code POST /api/v1/public/contact} request body. */
    public record ContactRequest(
            @NotBlank(message = "name is required") @Size(max = 200) String name,
            @NotBlank(message = "email is required")
            @Email(message = "email must be a valid address")
            @Size(max = 255) String email,
            @NotBlank(message = "message is required") @Size(max = 5000) String message,
            @Size(max = 200) String organization) {
    }
}
