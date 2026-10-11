package com.company.exam.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.service.PasswordResetService;
import com.company.exam.identity.service.PasswordResetTokenService;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Concurrency guarantees ({@code specs/10-P1-Identity-V1.5.md §177, §130}).
 *
 * <p>Proves that a single-use reset token cannot be consumed twice under concurrent requests, and
 * that the database unique constraint prevents duplicate users even under a race.
 */
class IdentityConcurrencyIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordResetTokenService resetTokenService;

    @Test
    void aSingleUseResetTokenCannotBeConsumedTwiceConcurrently() throws Exception {
        User user = createUser(syntheticEmail("concurrent-reset"), PASSWORD, "STUDENT");
        String token = resetTokenService.issue(user);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            ready.countDown();
            go.await(10, TimeUnit.SECONDS);
            try {
                passwordResetService.reset(token, "Another-Strong-Pass-1");
                return true;
            } catch (RuntimeException rejected) {
                return false;
            }
        };
        try {
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Boolean> results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void duplicateUserEmailIsPreventedUnderConcurrency() throws Exception {
        String email = syntheticEmail("concurrent-user");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            User user = new User();
            user.setOrganizationId(defaultOrganizationId());
            user.setFirstName("Race");
            user.setLastName("Condition");
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(PASSWORD));
            user.setStatus(Status.ACTIVE);
            ready.countDown();
            go.await(10, TimeUnit.SECONDS);
            try {
                userRepository.saveAndFlush(user);
                return true;
            } catch (DataIntegrityViolationException conflict) {
                return false;
            }
        };
        try {
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Boolean> results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }
}
