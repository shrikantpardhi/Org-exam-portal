package com.company.exam.publicapi;

import com.company.exam.common.exception.RateLimitedException;
import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public landing-page contact form ({@code specs/05 §73.2}; ADR-0037).
 *
 * <p>Validated by the controller, rate-limited per source IP here, and recorded as a
 * {@code background_job} of type {@code CONTACT_REQUEST}. It creates no user and writes no
 * business data; the job is the durable handoff to a downstream support flow.
 */
@Service
public class PublicContactService {

    private static final String JOB_TYPE = "CONTACT_REQUEST";

    private final BackgroundJobRepository backgroundJobRepository;
    private final ContactRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public PublicContactService(BackgroundJobRepository backgroundJobRepository, ContactRateLimiter rateLimiter,
            ObjectMapper objectMapper) {
        this.backgroundJobRepository = backgroundJobRepository;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void submit(PublicContactDtos.ContactRequest request, String ip) {
        if (!rateLimiter.allow(ip)) {
            throw new RateLimitedException("Too many requests");
        }
        BackgroundJob job = new BackgroundJob();
        job.setJobType(JOB_TYPE);
        job.setStatus(BackgroundJobStatus.QUEUED);
        job.setQueuedOn(Instant.now());
        job.setParameters(parameters(request));
        backgroundJobRepository.save(job);
    }

    private String parameters(PublicContactDtos.ContactRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", request.name());
        params.put("email", request.email());
        params.put("message", request.message());
        params.put("organization", request.organization());
        try {
            return objectMapper.writeValueAsString(params);
        } catch (RuntimeException unwritable) {
            return "{}";
        }
    }
}
