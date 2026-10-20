package com.company.exam.delivery.application;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.delivery.entity.AttemptStatus;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.entity.StudentResponse;
import com.company.exam.delivery.exception.StaleResponseException;
import com.company.exam.delivery.repository.AttemptQuestionRepository;
import com.company.exam.delivery.repository.StudentResponseRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Response validation and save with HTTP conditional (stale-write) protection
 * ({@code specs/14-P5-Delivery-V1.5.md §42-§47, §86}).
 *
 * <p>A save is an UPSERT on {@code UNIQUE(attempt_id, question_version_id)}: it never creates a
 * second row. The ETag is {@code resp-{updatedOnMillis}-{crc32OfResponse}}, derived from
 * {@code updated_on} — no {@code response_version} column exists. An older delayed request never
 * overwrites a newer accepted response: a mismatched {@code If-Match} returns 412
 * {@code STALE_RESPONSE}.
 */
@Service
public class ResponseSaveService {

    private static final int MAX_TIME_SPENT_SECONDS = 24 * 60 * 60;

    private final StudentResponseRepository responseRepository;
    private final AttemptQuestionRepository questionRepository;
    private final AttemptEligibilityService eligibilityService;
    private final AssessmentSessionService sessionService;
    private final com.company.exam.delivery.observability.DeliveryMetrics metrics;
    private final DeadlineEnforcementService deadlineEnforcement;

    public ResponseSaveService(StudentResponseRepository responseRepository,
            AttemptQuestionRepository questionRepository, AttemptEligibilityService eligibilityService,
            AssessmentSessionService sessionService,
            com.company.exam.delivery.observability.DeliveryMetrics metrics,
            DeadlineEnforcementService deadlineEnforcement) {
        this.responseRepository = responseRepository;
        this.questionRepository = questionRepository;
        this.eligibilityService = eligibilityService;
        this.sessionService = sessionService;
        this.metrics = metrics;
        this.deadlineEnforcement = deadlineEnforcement;
    }

    @Transactional(readOnly = true)
    public DeliveryDtos.ResponseView get(UUID attemptId, UUID questionVersionId, UUID userId) {
        eligibilityService.requireOwned(attemptId, userId);
        requireAssigned(attemptId, questionVersionId);
        StudentResponse response = responseRepository.findByAttemptIdAndQuestionVersionId(attemptId, questionVersionId)
                .orElseThrow(() -> NotFoundException.of("Response", questionVersionId));
        return toView(response, etag(response));
    }

    /** All persisted responses for the attempt, used for recovery and review ({@code §46.3}). */
    @Transactional(readOnly = true)
    public List<DeliveryDtos.ResponseView> list(UUID attemptId, UUID userId) {
        eligibilityService.requireOwned(attemptId, userId);
        return responseRepository.findByAttemptId(attemptId).stream()
                .map(response -> toView(response, etag(response)))
                .toList();
    }

    @Transactional
    public DeliveryDtos.ResponseView save(UUID attemptId, UUID questionVersionId,
            DeliveryDtos.ResponseSaveRequest request, String ifMatch, UUID userId) {
        return metrics.responseSave(() -> saveResponse(attemptId, questionVersionId, request, ifMatch, userId));
    }

    private DeliveryDtos.ResponseView saveResponse(UUID attemptId, UUID questionVersionId,
            DeliveryDtos.ResponseSaveRequest request, String ifMatch, UUID userId) {
        StudentAttempt attempt = eligibilityService.requireOwned(attemptId, userId);
        if (attempt.getAttemptStatus() != AttemptStatus.IN_PROGRESS) {
            throw new InvalidStateException("Attempt is not in progress");
        }
        // The server is authoritative for the deadline: a post-deadline save is refused (§54, §56).
        deadlineEnforcement.assertNotExpired(attempt);
        requireAssigned(attemptId, questionVersionId);
        validate(request);

        StudentResponse existing = responseRepository
                .findByAttemptIdAndQuestionVersionId(attemptId, questionVersionId)
                .orElse(null);
        verifyPrecondition(ifMatch, existing);

        StudentResponse target = existing != null ? existing : new StudentResponse();
        if (existing == null) {
            target.setAttemptId(attemptId);
            target.setQuestionVersionId(questionVersionId);
        }
        target.setResponse(normalize(request));
        target.setTimeSpentSeconds(request.timeSpentSeconds());
        target.setMarkedForReview(request.markedForReview() == null ? Boolean.FALSE : request.markedForReview());
        target.setVisited(request.visited() == null ? Boolean.TRUE : request.visited());
        StudentResponse saved = responseRepository.saveAndFlush(target);
        sessionService.touch(attemptId, Instant.now());
        return toView(saved, etag(saved));
    }

    private void verifyPrecondition(String ifMatch, StudentResponse existing) {
        if (ifMatch == null) {
            return; // No If-Match: accept (first save).
        }
        String provided = stripQuotes(ifMatch.trim());
        if ("*".equals(provided)) {
            if (existing == null) {
                throw new StaleResponseException();
            }
            return;
        }
        if (existing == null || !provided.equals(etag(existing))) {
            metrics.staleResponse();
            throw new StaleResponseException();
        }
    }

    private void requireAssigned(UUID attemptId, UUID questionVersionId) {
        if (!questionRepository.existsByAttemptIdAndQuestionVersionId(attemptId, questionVersionId)) {
            throw new ValidationException("Question version is not assigned to this attempt",
                    List.of(ApiError.of("questionVersionId", "QUESTION_NOT_ASSIGNED",
                            "The question version is not part of the prepared attempt")));
        }
    }

    private void validate(DeliveryDtos.ResponseSaveRequest request) {
        if (request == null) {
            throw new ValidationException("Response payload is required");
        }
        String response = normalize(request);
        if (response != null && response.length() > 10000) {
            throw new ValidationException("Response exceeds the permitted size",
                    List.of(ApiError.of("response", "RESPONSE_TOO_LONG",
                            "Response content exceeds the permitted length")));
        }
        Integer seconds = request.timeSpentSeconds();
        if (seconds != null && (seconds < 0 || seconds > MAX_TIME_SPENT_SECONDS)) {
            throw new ValidationException("timeSpentSeconds is invalid",
                    List.of(ApiError.of("timeSpentSeconds", "INVALID_TIME_SPENT",
                            "Time spent must be a non-negative, plausible value")));
        }
    }

    private String normalize(DeliveryDtos.ResponseSaveRequest request) {
        String response = request.response();
        return response == null ? null : response.trim();
    }

    private DeliveryDtos.ResponseView toView(StudentResponse response, String etag) {
        return new DeliveryDtos.ResponseView(response.getAttemptId(), response.getQuestionVersionId(),
                response.getResponse(), response.getTimeSpentSeconds(),
                Boolean.TRUE.equals(response.getMarkedForReview()), Boolean.TRUE.equals(response.getVisited()), etag);
    }

    /** {@code resp-{updatedOnMillis}-{crc32OfResponse}}, unquoted; the HTTP layer adds quotes. */
    static String etag(StudentResponse response) {
        Instant updatedOn = response.getUpdatedOn();
        long millis = updatedOn == null ? Instant.now().toEpochMilli() : updatedOn.toEpochMilli();
        CRC32 crc = new CRC32();
        crc.update((response.getResponse() == null ? "" : response.getResponse()).getBytes(StandardCharsets.UTF_8));
        return "resp-" + millis + "-" + crc.getValue();
    }

    static String stripQuotes(String value) {
        if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
