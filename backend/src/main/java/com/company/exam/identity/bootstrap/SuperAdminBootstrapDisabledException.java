package com.company.exam.identity.bootstrap;

/**
 * Raised when SUPER_ADMIN bootstrap is attempted in an environment that forbids it (ADR-0032).
 *
 * <p>This is a hard failure, not a warning: creating the installation owner with a known credential in
 * production must fail loudly.
 */
public class SuperAdminBootstrapDisabledException extends RuntimeException {

    public SuperAdminBootstrapDisabledException(String message) {
        super(message);
    }
}
