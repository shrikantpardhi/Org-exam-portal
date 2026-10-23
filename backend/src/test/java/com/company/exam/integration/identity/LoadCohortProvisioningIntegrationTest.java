package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.loadcohort.LoadCohortDtos;
import com.company.exam.identity.loadcohort.LoadCohortProvisioningService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Synthetic load-cohort provisioning ({@code specs/10-P1-Identity-V1.5.md §56}; closes GAP-085).
 *
 * <p>These tests exercise the mechanism the way the load seed does: opt in through the property, then
 * provision a cohort and prove the three properties that matter — the accounts are idempotent, their
 * credentials actually authenticate, and every credential is a real hash rather than the plaintext
 * {@code {noop}} value the old SQL path wrote.
 */
@TestPropertySource(properties = "app.load-cohort.enabled=true")
class LoadCohortProvisioningIntegrationTest extends AbstractIdentityIntegrationTest {

    @Autowired
    private LoadCohortProvisioningService provisioningService;

    private static List<LoadCohortDtos.CohortMember> cohort(String prefix, int size) {
        return java.util.stream.IntStream.rangeClosed(1, size)
                .mapToObj(i -> new LoadCohortDtos.CohortMember(
                        prefix + i + "@example.test", "Load", "Student" + i))
                .toList();
    }

    @Test
    void provisionsEachMemberWithTheStudentRole() {
        String prefix = "prov-" + java.util.UUID.randomUUID() + "-";
        List<LoadCohortDtos.CohortMember> members = cohort(prefix, 3);

        LoadCohortDtos.ProvisioningResult result = provisioningService.provision(members);

        assertThat(result.created()).isEqualTo(3);
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isZero();

        for (LoadCohortDtos.MemberOutcome outcome : result.members()) {
            assertThat(outcome.action()).isEqualTo("CREATED");
            assertThat(outcome.credential()).isNotBlank();
            User user = userRepository.findByEmail(outcome.email()).orElseThrow();
            assertThat(user.getStatus()).isEqualTo(Status.ACTIVE);
            assertThat(user.getId()).isEqualTo(outcome.userId());
            assertThat(roleRepository.findEffectiveRoleCodes(user.getId())).contains("STUDENT");
        }
    }

    /**
     * The stored value must be a real encoder hash, never the plaintext that the old SQL path inserted.
     *
     * <p>This is the security property GAP-085 was really about: a {@code {noop}} credential would mean the
     * cohort's passwords sat in the database in clear text.
     */
    @Test
    void storesAHashRatherThanThePlaintextCredential() {
        String prefix = "hash-" + java.util.UUID.randomUUID() + "-";
        LoadCohortDtos.ProvisioningResult result = provisioningService.provision(cohort(prefix, 1));
        LoadCohortDtos.MemberOutcome outcome = result.members().get(0);

        User user = userRepository.findByEmail(outcome.email()).orElseThrow();

        assertThat(user.getPassword()).isNotEqualTo(outcome.credential());
        assertThat(user.getPassword()).doesNotContain(outcome.credential());
        assertThat(user.getPassword()).doesNotStartWith("{noop}");
        // The platform's delegating encoder writes a {id} prefix naming the real algorithm.
        assertThat(user.getPassword()).startsWith("{");
        assertThat(passwordEncoder.matches(outcome.credential(), user.getPassword())).isTrue();
    }

    /**
     * Re-running the seed must not duplicate users (GAP-085 deliverable 3, second criterion).
     *
     * <p>The second run re-issues the credential rather than creating a second row, and the newly issued
     * credential is the one that works afterwards.
     */
    @Test
    void rerunningDoesNotDuplicateUsers() throws Exception {
        String prefix = "idem-" + java.util.UUID.randomUUID() + "-";
        List<LoadCohortDtos.CohortMember> members = cohort(prefix, 2);

        LoadCohortDtos.ProvisioningResult first = provisioningService.provision(members);
        long afterFirst = members.stream()
                .map(m -> userRepository.findByEmail(m.email()))
                .filter(Optional::isPresent)
                .count();
        assertThat(afterFirst).isEqualTo(2);

        LoadCohortDtos.ProvisioningResult second = provisioningService.provision(members);

        assertThat(second.created()).isZero();
        assertThat(second.updated()).isEqualTo(2);

        for (LoadCohortDtos.CohortMember member : members) {
            // Exactly one row per email still exists — no duplicate was created.
            assertThat(userRepository.findByEmail(member.email())).isPresent();
            assertThat(userRepository.findAll().stream()
                    .filter(u -> member.email().equalsIgnoreCase(u.getEmail()))
                    .count()).isEqualTo(1);
        }

        // The re-issued credential supersedes the first one and still authenticates.
        String reissued = second.members().get(0).credential();
        assertThat(reissued).isNotEqualTo(first.members().get(0).credential());
        assertThat(login(members.get(0).email(), reissued)).isNotBlank();
    }

    /** GAP-085 deliverable 3, third criterion: the issued credentials are usable for login. */
    @Test
    void issuedCredentialsAuthenticateThroughTheRealLoginEndpoint() throws Exception {
        String prefix = "login-" + java.util.UUID.randomUUID() + "-";
        List<LoadCohortDtos.CohortMember> members = cohort(prefix, 2);

        LoadCohortDtos.ProvisioningResult result = provisioningService.provision(members);

        for (LoadCohortDtos.MemberOutcome outcome : result.members()) {
            String token = login(outcome.email(), outcome.credential());
            assertThat(token).isNotBlank();
        }
    }

    /**
     * Provisioning must refuse a real-looking address, so a mistyped argument cannot reset a real account.
     */
    @Test
    void refusesNonSyntheticEmailAddresses() {
        List<LoadCohortDtos.CohortMember> members = List.of(new LoadCohortDtos.CohortMember(
                "real.person@example.com", "Not", "Synthetic"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> provisioningService.provision(members))
                .isInstanceOf(com.company.exam.common.exception.BusinessException.class)
                .hasMessageContaining(".test");

        assertThat(userRepository.findByEmail("real.person@example.com")).isEmpty();
    }

    /**
     * Every created account leaves an audit trail: the security event *and* the row's own audit columns.
     *
     * <p>The CLI runs with no authenticated principal, so the JPA auditor would leave {@code created_by}
     * null. The service attributes the row to the system actor explicitly; this asserts that attribution
     * survives, because an unattributed account is one nobody can trace back to the provisioning step.
     */
    @Test
    void attributesTheAccountToTheSystemActor() {
        String prefix = "audit-" + java.util.UUID.randomUUID() + "-";
        LoadCohortDtos.ProvisioningResult result = provisioningService.provision(cohort(prefix, 1));

        assertThat(result.members()).hasSize(1);
        assertThat(result.members().get(0).userId()).isNotNull();

        User user = userRepository.findByEmail(result.members().get(0).email()).orElseThrow();
        assertThat(user.getCreatedBy()).isEqualTo(
                com.company.exam.identity.IdentityConstants.SYSTEM_ACTOR_ID);
        assertThat(user.getUpdatedBy()).isEqualTo(
                com.company.exam.identity.IdentityConstants.SYSTEM_ACTOR_ID);
        assertThat(user.getCreatedOn()).isNotNull();
    }
}
