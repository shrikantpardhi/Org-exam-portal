package com.company.exam.identity.bootstrap;

import java.util.UUID;

/** Wire shapes for the SUPER_ADMIN bootstrap CLI (ADR-0032). */
public final class SuperAdminBootstrapDtos {

    private SuperAdminBootstrapDtos() {
    }

    /** The outcome written to stdout. {@code userId} is null when it already existed. */
    public record BootstrapResult(String outcome, UUID userId) {
    }
}
