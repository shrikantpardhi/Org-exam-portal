package com.company.exam.integration.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.common.job.JobTypes;
import com.company.exam.communication.gateway.FakeEmailGateway;
import com.company.exam.communication.service.EmailDeliveryService;
import com.company.exam.communication.service.NotificationService;
import com.company.exam.communication.worker.AnnouncementFanoutWorker;
import com.company.exam.communication.worker.EmailDeliveryWorker;
import com.company.exam.communication.worker.NotificationRequestWorker;
import com.company.exam.identity.entity.User;
import com.company.exam.integration.evaluation.AbstractEvaluationIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * P10 communication end-to-end: the notification center, asynchronous announcement fan-out, bounded
 * email retry with backoff, and provider failure isolation
 * ({@code specs/19-P10-Communication-V1.5.md §8-§14, §19, §46-§51, §33-§36, §65}).
 */
class CommunicationIntegrationTest extends AbstractEvaluationIntegrationTest {

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private EmailDeliveryService emailDeliveryService;
    @Autowired
    private NotificationRequestWorker notificationRequestWorker;
    @Autowired
    private AnnouncementFanoutWorker announcementFanoutWorker;
    @Autowired
    private EmailDeliveryWorker emailDeliveryWorker;
    @Autowired
    private FakeEmailGateway emailGateway;
    @Autowired
    private BackgroundJobRepository jobRepository;

    @BeforeEach
    void resetGateway() {
        emailGateway.reset();
    }

    // ------------------------------------------------------------------ notification center

    @Test
    void notificationCenterIsSelfScopedAndReadIsIdempotent() throws Exception {
        User owner = newStudent("p10-owner");
        User other = newStudent("p10-other");
        String ownerToken = tokenFor(owner);
        String otherToken = tokenFor(other);

        notificationService.notifyUsers(List.of(owner.getId()), "Result published", "Your result is available.");
        notificationService.notifyUsers(List.of(other.getId()), "Other", "Other message");

        String list = mockMvc.perform(get("/api/v1/notifications").header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].notificationStatus").value("UNREAD"))
                .andReturn().getResponse().getContentAsString();
        String notificationId = JsonPath.read(list, "$.data[0].id");

        // Read is idempotent: UNREAD -> READ, then READ -> READ.
        mockMvc.perform(post("/api/v1/notifications/" + notificationId + "/read")
                        .header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/notifications/" + notificationId + "/read")
                        .header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications?status=UNREAD").header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // A student cannot read another student's notification.
        String otherList = mockMvc.perform(get("/api/v1/notifications").header(AUTHORIZATION, bearer(otherToken)))
                .andReturn().getResponse().getContentAsString();
        String otherNotificationId = JsonPath.read(otherList, "$.data[0].id");
        mockMvc.perform(post("/api/v1/notifications/" + otherNotificationId + "/read")
                        .header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNotFound());

        // Mark-all-read is scoped to the caller.
        notificationService.notifyUsers(List.of(owner.getId()), "Second", "Second message");
        assertThat(notificationService.unreadCount(owner.getId())).isEqualTo(1);
        mockMvc.perform(post("/api/v1/notifications/read-all").header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk());
        assertThat(notificationService.unreadCount(owner.getId())).isZero();
        assertThat(notificationService.unreadCount(other.getId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ announcement fan-out

    @Test
    void announcementPublishQueuesAsynchronousFanOut() throws Exception {
        User student = newStudent("p10-announce");
        String admin = adminToken();

        String created = mockMvc.perform(post("/api/v1/announcements")
                        .header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"title\":\"Maintenance window\",\"description\":\"Scheduled maintenance.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String announcementId = JsonPath.read(created, "$.data.id");

        String published = mockMvc.perform(post("/api/v1/announcements/" + announcementId + "/publish")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String fanoutJobId = JsonPath.read(published, "$.data.fanoutJobId");

        // Asynchronous: publishing returns before any recipient receives anything.
        assertThat(countNotifications(student.getId(), "Maintenance window")).isZero();
        BackgroundJob job = jobRepository.findById(UUID.fromString(fanoutJobId)).orElseThrow();
        assertThat(job.getJobType()).isEqualTo(JobTypes.ANNOUNCEMENT_FANOUT);
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);

        // The worker performs the fan-out.
        int processed = announcementFanoutWorker.processQueued();
        assertThat(processed).isGreaterThanOrEqualTo(1);
        assertThat(countNotifications(student.getId(), "Maintenance window")).isEqualTo(1);
        assertThat(jobRepository.findById(UUID.fromString(fanoutJobId)).orElseThrow().getStatus())
                .isEqualTo(BackgroundJobStatus.COMPLETED);

        // A retry of the same job does not fan out twice (§51).
        announcementFanoutWorker.processQueued();
        assertThat(countNotifications(student.getId(), "Maintenance window")).isEqualTo(1);

        // The student sees the published announcement.
        String visible = mockMvc.perform(get("/api/v1/announcements").header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(visible).contains("Maintenance window");
    }

    // ------------------------------------------------------------------ retry and backoff

    @Test
    void emailRetriesWithBackoffThenSucceeds() throws Exception {
        User student = newStudent("p10-retry");
        String admin = adminToken();
        // Publish an announcement so the fan-out creates a notification; the email path is driven directly.
        String created = mockMvc.perform(post("/api/v1/announcements").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON)
                        .content("{\"title\":\"Retry subject\",\"description\":\"Retry body\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/announcements/" + JsonPath.read(created, "$.data.id") + "/publish")
                .header(AUTHORIZATION, bearer(admin))).andExpect(status().isOk());

        BackgroundJob job = emailDeliveryService.enqueueEmail(student.getId(), "RESULT_PUBLISHED",
                Map.of("studentName", "Asha", "assessmentName", "Mock 1"));
        UUID jobId = job.getId();

        // First attempt fails transiently -> bounded retry, not a permanent failure.
        emailGateway.failTransiently(1);
        emailDeliveryWorker.processQueued();
        BackgroundJob failedOnce = jobRepository.findById(jobId).orElseThrow();
        assertThat(failedOnce.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(failedOnce.getAttemptCount()).isEqualTo(1);
        assertThat(failedOnce.getLastAttemptOn()).isNotNull();
        assertThat(emailGateway.sentCount()).isZero();

        // Not yet due: the backoff window has not elapsed, so no second attempt is made.
        assertThat(emailDeliveryWorker.due(failedOnce, java.time.Instant.now())).isFalse();
        emailDeliveryWorker.processQueued();
        assertThat(emailGateway.sentCount()).isZero();

        // Simulate the backoff window elapsing, then the retry succeeds.
        jdbcTemplate.update("update background_job set last_attempt_on = ? where id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofHours(1))), jobId);
        emailDeliveryWorker.processQueued();
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus())
                .isEqualTo(BackgroundJobStatus.COMPLETED);
        assertThat(emailGateway.sentCount()).isEqualTo(1);
        assertThat(emailGateway.sentEmails().get(0).htmlBody()).contains("Asha");
    }

    @Test
    void permanentlyInvalidDeliveryFailsWithoutRetry() {
        User student = newStudent("p10-permanent");
        // An unknown template is a permanent failure, never a retry loop (§34, §59).
        BackgroundJob job = emailDeliveryService.enqueueEmail(student.getId(), "NO_SUCH_TEMPLATE", Map.of());
        assertThat(emailDeliveryWorker.process(job).outcome())
                .isEqualTo(EmailDeliveryService.DeliveryResult.Outcome.FAILED);
        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(BackgroundJobStatus.FAILED);
    }

    // ------------------------------------------------------------------ isolation

    @Test
    void providerFailureDoesNotRollBackTheBusinessTransaction() throws Exception {
        // A commerce fixture; the payment callback is the business transaction under test.
        String admin = adminToken();
        UUID collectionId = UUID.randomUUID();
        jdbcTemplate.update("insert into assessment_collection (id, code, name, status, created_on) "
                + "values (?, ?, ?, 'ACTIVE', now())", collectionId, "P10-COL-" + collectionId, "P10 Collection");
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        String packageId = postCreated("/api/v1/access-packages",
                "{\"code\":\"P10-PKG-" + suffix + "\",\"name\":\"P10 Package\"}", admin);
        String planId = postCreated("/api/v1/subscription-plans", "{\"accessPackageId\":\"" + packageId
                + "\",\"code\":\"P10-PLAN-" + suffix + "\",\"name\":\"P10 30 days\",\"durationDays\":30,"
                + "\"price\":500.00}", admin);
        User student = newStudent("p10-isolation");
        String studentToken = tokenFor(student);
        String purchaseId = postCreated("/api/v1/purchases",
                "{\"subscriptionPlanId\":\"" + planId + "\"}", studentToken);

        String initiated = mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/payment")
                        .header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(initiated, "$.data.paymentReference");

        // The email provider is down for every subsequent send.
        emailGateway.failTransiently(100);
        BigDecimal amount = new BigDecimal("500.00");
        String signature = testGatewaySignature(reference, "CAPTURED", amount);
        mockMvc.perform(post("/api/v1/payments/callback").contentType(JSON)
                        .content("{\"paymentReference\":\"" + reference + "\",\"providerStatus\":\"CAPTURED\","
                                + "\"amount\":500.00,\"currency\":\"INR\",\"signature\":\"" + signature + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"));

        // The business state is authoritative and unaffected by communication.
        assertThat(jdbcTemplate.queryForObject("select payment_status from purchase where id = ?", String.class,
                UUID.fromString(purchaseId))).isEqualTo("SUCCESS");

        // The after-commit listener produced durable communication work for the buyer.
        Long queuedRequests = jdbcTemplate.queryForObject(
                "select count(*) from background_job where job_type = 'NOTIFICATION_REQUEST' "
                        + "and reference_type = 'PAYMENT_SUCCESSFUL'",
                Long.class);
        assertThat(queuedRequests).as("after-commit notification request").isGreaterThanOrEqualTo(1L);

        // The event produced durable communication work after commit; running it against a down
        // provider must not change the business state.
        for (int i = 0; i < 4; i++) {
            notificationRequestWorker.processQueued();
            emailDeliveryWorker.processQueued();
        }
        assertThat(jdbcTemplate.queryForObject("select payment_status from purchase where id = ?", String.class,
                UUID.fromString(purchaseId))).isEqualTo("SUCCESS");
        assertThat(countNotifications(student.getId(), "Payment successful")).isEqualTo(1);
        assertThat(emailGateway.sentCount()).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private String postCreated(String url, String body, String token) throws Exception {
        String response = mockMvc.perform(post(url).header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private long countNotifications(UUID userId, String title) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from notification where user_id = ? and title = ?", Long.class, userId, title);
        return count == null ? 0 : count;
    }

    private String testGatewaySignature(String reference, String status, BigDecimal amount) {
        return testGateway.signatureFor(reference, status, amount, "INR");
    }

    @Autowired
    private com.company.exam.commerce.gateway.TestPaymentGateway testGateway;
}
