package com.company.exam.identity.loadcohort;

import java.util.List;

/**
 * Request and result types for synthetic load-cohort provisioning
 * ({@code specs/10-P1-Identity-V1.5.md §56}; GAP-085).
 *
 * <p>The requesting party supplies only non-secret identity data. The credential itself is generated
 * inside the service and is never accepted from the caller, so a caller cannot downgrade the cohort to a
 * chosen password.
 */
public final class LoadCohortDtos {

    private LoadCohortDtos() {
    }

    /**
     * One synthetic account to provision.
     *
     * @param email     the account email; must be unique and look synthetic
     * @param firstName given name
     * @param lastName  family name
     */
    public record CohortMember(String email, String firstName, String lastName) {
    }

    /**
     * Result of one provisioning invocation.
     *
     * @param created number of accounts newly created
     * @param updated number of existing accounts whose credential was re-issued
     * @param skipped number of members left untouched because nothing needed to change
     * @param members per-account outcome, in request order
     */
    public record ProvisioningResult(int created, int updated, int skipped, List<MemberOutcome> members) {

        public ProvisioningResult {
            members = List.copyOf(members);
        }
    }

    /**
     * The outcome for a single account.
     *
     * <p>The plaintext credential is carried here exactly once, so the caller can hand it to the load
     * generator. It is never logged and never persisted: the database stores only the hash.
     *
     * @param email      the account email
     * @param userId     the account id
     * @param credential the generated password, or {@code null} when the account was skipped
     * @param action     {@code CREATED}, {@code UPDATED} or {@code SKIPPED}
     */
    public record MemberOutcome(String email, java.util.UUID userId, String credential, String action) {
    }
}
