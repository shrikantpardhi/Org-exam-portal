package com.company.exam.communication.service;

import com.company.exam.common.audit.CurrentAuditorProvider;
import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.common.job.JobTypes;
import com.company.exam.communication.config.CommunicationProperties;
import com.company.exam.communication.entity.EmailTemplate;
import com.company.exam.communication.gateway.EmailGateway;
import com.company.exam.communication.gateway.EmailGatewayRegistry;
import com.company.exam.communication.observability.CommunicationMetrics;
import com.company.exam.identity.api.UserDirectory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Asynchronous email delivery ({@code specs/19-P10-Communication-V1.5.md §29-§38}).
 *
 * <p>Business modules never send email. A business event or an announcement produces a durable
 * {@code background_job}; this service renders the template and hands the message to the
 * {@link EmailGateway} from a worker, so external latency and failures stay out of the business
 * transaction ({@code §6, §19, §32}).
 */
@Service
public class EmailDeliveryService {

    private final BackgroundJobRepository jobRepository;
    private final EmailTemplateService templateService;
    private final TemplateRenderer renderer;
    private final EmailGatewayRegistry gatewayRegistry;
    private final UserDirectory userDirectory;
    private final CommunicationMetrics metrics;
    private final com.company.exam.content.rendering.ContentSanitizer sanitizer;
    private final int maxAttempts;
    private final ObjectMapper objectMapper;

    public EmailDeliveryService(BackgroundJobRepository jobRepository, EmailTemplateService templateService,
            TemplateRenderer renderer, EmailGatewayRegistry gatewayRegistry, UserDirectory userDirectory,
            CommunicationMetrics metrics, com.company.exam.content.rendering.ContentSanitizer sanitizer,
            CommunicationProperties properties, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.templateService = templateService;
        this.renderer = renderer;
        this.gatewayRegistry = gatewayRegistry;
        this.userDirectory = userDirectory;
        this.metrics = metrics;
        this.sanitizer = sanitizer;
        this.maxAttempts = properties.getMaxAttempts();
        this.objectMapper = objectMapper;
    }

    /** Parameters of an email delivery job. */
    public record EmailJobParameters(UUID userId, String templateCode, Map<String, String> variables) {
    }

    /** Parameters of a durable notification request produced from a business event. */
    public record NotificationJobParameters(String eventType, List<UUID> userIds, String title, String message,
            String templateCode, Map<String, String> variables) {
    }

    /** The outcome of one delivery attempt. */
    public record DeliveryResult(Outcome outcome, String detail) {

        public enum Outcome {
            SENT,
            RETRY,
            FAILED
        }

        static DeliveryResult sent(String reference) {
            return new DeliveryResult(Outcome.SENT, reference);
        }

        static DeliveryResult retry(String detail) {
            return new DeliveryResult(Outcome.RETRY, detail);
        }

        static DeliveryResult failed(String detail) {
            return new DeliveryResult(Outcome.FAILED, detail);
        }
    }

    /**
     * Enqueues a durable email delivery job for one recipient.
     *
     * <p>{@code REQUIRES_NEW} because this is also called from an {@code AFTER_COMMIT} listener and
     * from workers: those have no live transaction of their own, and joining a finished transaction
     * would silently discard the insert.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public BackgroundJob enqueueEmail(UUID userId, String templateCode, Map<String, String> variables) {
        BackgroundJob job = new BackgroundJob();
        job.setJobType(JobTypes.EMAIL_DELIVERY);
        job.setReferenceType("USER");
        job.setReferenceId(userId);
        job.setInitiatedBy(CurrentAuditorProvider.SYSTEM_ACTOR_ID);
        job.setStatus(BackgroundJobStatus.QUEUED);
        job.setParameters(write(new EmailJobParameters(userId, templateCode, variables == null ? Map.of() : variables)));
        job.setQueuedOn(Instant.now());
        job.setProgressCurrent(0);
        job.setProgressTotal(1);
        job.setAttemptCount(0);
        return jobRepository.save(job);
    }

    /**
     * Enqueues a durable notification request produced from a business event.
     *
     * <p>{@code REQUIRES_NEW} is required: the caller is the {@code AFTER_COMMIT} listener, whose
     * original transaction has already committed, so the job must be written in its own transaction
     * ({@code §19, §32}).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public BackgroundJob enqueueNotificationRequest(NotificationJobParameters parameters) {
        BackgroundJob job = new BackgroundJob();
        job.setJobType(JobTypes.NOTIFICATION_REQUEST);
        job.setReferenceType(parameters.eventType());
        job.setInitiatedBy(CurrentAuditorProvider.SYSTEM_ACTOR_ID);
        job.setStatus(BackgroundJobStatus.QUEUED);
        job.setParameters(write(parameters));
        job.setQueuedOn(Instant.now());
        job.setProgressCurrent(0);
        job.setProgressTotal(parameters.userIds() == null ? 0 : parameters.userIds().size());
        job.setAttemptCount(0);
        return jobRepository.save(job);
    }

    public NotificationJobParameters readNotificationJob(String parameters) {
        return read(parameters, NotificationJobParameters.class);
    }

    public EmailJobParameters readEmailJob(String parameters) {
        return read(parameters, EmailJobParameters.class);
    }

    /**
     * Performs one delivery attempt. It is <strong>not</strong> transactional: the worker owns the
     * job state transition. A missing/inactive template or an unknown recipient is a permanent
     * failure; a provider timeout or transient error is retryable ({@code §28, §34, §59}).
     */
    public DeliveryResult attempt(BackgroundJob job) {
        EmailJobParameters parameters = readEmailJob(job.getParameters());
        Optional<EmailTemplate> template = templateService.resolveActive(parameters.templateCode());
        if (template.isEmpty()) {
            metrics.templateMissing();
            return DeliveryResult.failed("TEMPLATE_NOT_AVAILABLE");
        }
        String address = userDirectory.find(parameters.userId())
                .map(UserDirectory.UserView::email)
                .filter(value -> value != null && !value.isBlank())
                .orElse(null);
        if (address == null) {
            return DeliveryResult.failed("NO_RECIPIENT_ADDRESS");
        }
        Map<String, String> variables = parameters.variables() == null ? Map.of() : parameters.variables();
        String subject = renderer.render(template.get().getSubject(), variables);
        String body = sanitizer.sanitize(renderer.render(template.get().getBody(), variables));
        metrics.emailAttempt();
        long startedAt = System.nanoTime();
        EmailGateway.EmailSendResult result = gatewayRegistry.active()
                .send(new EmailGateway.RenderedEmail(address, subject, body));
        if (result.sent()) {
            metrics.emailSent(Duration.ofNanos(System.nanoTime() - startedAt));
            return DeliveryResult.sent(result.providerReference());
        }
        metrics.emailFailed();
        return result.retryable() ? DeliveryResult.retry("PROVIDER_TRANSIENT_FAILURE")
                : DeliveryResult.failed("PROVIDER_PERMANENT_FAILURE");
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    private String write(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private <T> T read(String json, Class<T> type) {
        return objectMapper.readValue(json, type);
    }
}
