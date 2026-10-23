package com.company.exam.identity.loadcohort;

/**
 * Raised when synthetic load-cohort provisioning is attempted in an environment that forbids it
 * ({@code specs/10-P1-Identity-V1.5.md §56}; GAP-085).
 *
 * <p>This is a hard failure, not a warning. If provisioning is ever reachable in production it must fail
 * loudly rather than quietly creating accounts with known credentials.
 */
public class LoadCohortDisabledException extends RuntimeException {

    public LoadCohortDisabledException(String message) {
        super(message);
    }
}
