package com.company.exam.reporting.service;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.AuthorizationException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.common.job.BackgroundJob;
import com.company.exam.common.job.BackgroundJobRepository;
import com.company.exam.common.job.BackgroundJobStatus;
import com.company.exam.common.job.JobTypes;
import com.company.exam.reporting.catalog.ReportCatalog;
import com.company.exam.reporting.catalog.ReportFormat;
import com.company.exam.reporting.catalog.ReportType;
import com.company.exam.reporting.dto.ReportingDtos;
import com.company.exam.reporting.observability.ReportingMetrics;
import com.company.exam.reporting.projection.ReportCriteria;
import com.company.exam.reporting.projection.ReportDataProvider;
import com.company.exam.reporting.projection.ReportProjection;
import com.company.exam.reporting.renderer.ReportRenderer;
import com.company.exam.reporting.storage.ReportArtifactStore;
import com.company.exam.reporting.validation.ReportRequestValidator;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * The report application service ({@code specs/22-P13-Reporting-V1.5.md §5, §6, §8, §22, §24-§31}).
 *
 * <p>Reports are strictly read-only ({@code §7.3}). Small bounded reports execute synchronously;
 * large ones are queued as a general-purpose {@code background_job} so no new reporting table is
 * required ({@code §25, §30}). All authorization, filter validation, and object scoping are decided
 * here on the server ({@code §7.4, §74}).
 */
@Service
public class ReportService {

    private final ReportCatalog catalog;
    private final ReportRequestValidator validator;
    private final ReportRenderer renderer;
    private final ReportArtifactStore artifactStore;
    private final BackgroundJobRepository jobRepository;
    private final ReportingMetrics metrics;
    private final SecurityEventLogger audit;
    private final ObjectMapper objectMapper;
    private final Map<ReportType, ReportDataProvider> providers = new EnumMap<>(ReportType.class);

    public ReportService(ReportCatalog catalog, ReportRequestValidator validator, ReportRenderer renderer,
            ReportArtifactStore artifactStore, BackgroundJobRepository jobRepository, ReportingMetrics metrics,
            SecurityEventLogger audit, ObjectMapper objectMapper, List<ReportDataProvider> providers) {
        this.catalog = catalog;
        this.validator = validator;
        this.renderer = renderer;
        this.artifactStore = artifactStore;
        this.jobRepository = jobRepository;
        this.metrics = metrics;
        this.audit = audit;
        this.objectMapper = objectMapper;
        providers.forEach(provider -> this.providers.put(provider.reportType(), provider));
    }

    /** Parameters persisted for a queued report job; it is a sanitized request fingerprint (§30). */
    public record ReportJobParameters(String reportType, String format, Map<String, String> filters) {
    }

    /** The outcome of a request: either a completed synchronous report or a queued job. */
    public record ReportExecution(ReportingDtos.ReportResult result, ReportingDtos.ReportAccepted accepted) {
    }

    @Transactional(readOnly = true)
    public List<ReportingDtos.ReportCatalogEntry> catalogEntries() {
        return catalog.all().stream().map(this::toCatalogEntry).toList();
    }

    @Transactional
    public ReportExecution request(ReportingDtos.ReportRequest request, UUID callerId, boolean callerIsStudent) {
        ReportType type = validator.resolveType(request.reportType());
        ReportFormat format = validator.resolveFormat(type, request.format());
        validator.validateFilters(type, request.filters());
        Map<String, String> filters = scopedFilters(type, request.filters(), callerId, callerIsStudent);

        if (type.executionMode() == com.company.exam.reporting.catalog.ReportExecutionMode.SYNC) {
            return new ReportExecution(runSynchronously(type, format, filters, callerId, callerIsStudent), null);
        }
        BackgroundJob job = enqueue(type, format, filters, callerId);
        metrics.queued(type.reportType());
        audit.audit(callerId, "QUEUE", "Report", job.getId(), "Queued " + type.reportType() + " report");
        return new ReportExecution(null, new ReportingDtos.ReportAccepted(job.getId(), job.getStatus().name()));
    }

    /** Runs a small report inside the request and stores its artifact like any other report. */
    private ReportingDtos.ReportResult runSynchronously(ReportType type, ReportFormat format,
            Map<String, String> filters, UUID callerId, boolean callerIsStudent) {
        Instant startedAt = Instant.now();
        ReportProjection projection = project(type, filters, callerId, callerIsStudent);
        byte[] artifact = renderer.render(projection, format);
        String fileName = artifactName(type, filters, format);
        UUID artifactId = UUID.randomUUID();
        String storageKey = artifactStore.write(artifactId, fileName, artifact);
        Duration latency = Duration.between(startedAt, Instant.now());
        metrics.generated(type.reportType(), projection.rowCount(), latency);
        audit.audit(callerId, "GENERATE", "Report", artifactId,
                "Generated " + type.reportType() + " report with " + projection.rowCount() + " rows");
        return new ReportingDtos.ReportResult(type.reportType(), format.name(), projection.rowCount(), fileName,
                null, projection.rows(), projection.metrics().stream()
                        .map(metric -> new ReportingDtos.MetricView(metric.name(), metric.value(),
                                metric.definition()))
                        .toList(),
                projection.freshnessNote());
    }

    private BackgroundJob enqueue(ReportType type, ReportFormat format, Map<String, String> filters, UUID callerId) {
        BackgroundJob job = new BackgroundJob();
        job.setJobType(JobTypes.REPORT_GENERATION);
        job.setReferenceType(type.reportType());
        job.setInitiatedBy(callerId);
        job.setStatus(BackgroundJobStatus.QUEUED);
        job.setParameters(objectMapper.writeValueAsString(new ReportJobParameters(type.reportType(), format.name(),
                filters)));
        job.setQueuedOn(Instant.now());
        job.setProgressCurrent(0);
        job.setProgressTotal(1);
        job.setAttemptCount(0);
        return jobRepository.saveAndFlush(job);
    }

    /**
     * Executes a report for the worker ({@code §93}) and stores the artifact. Kept separate from the
     * request path so a queued report never runs on the request thread.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generate(BackgroundJob job) {
        ReportJobParameters parameters = objectMapper.readValue(job.getParameters(), ReportJobParameters.class);
        ReportType type = validator.resolveType(parameters.reportType());
        ReportFormat format = validator.resolveFormat(type, parameters.format());
        Instant startedAt = Instant.now();
        ReportProjection projection = project(type, parameters.filters(), job.getInitiatedBy(), false);
        byte[] artifact = renderer.render(projection, format);
        String storageKey = artifactStore.write(job.getId(), artifactName(type, parameters.filters(), format),
                artifact);
        metrics.generated(type.reportType(), projection.rowCount(), Duration.between(startedAt, Instant.now()));
        return storageKey;
    }

    private ReportProjection project(ReportType type, Map<String, String> filters, UUID callerId,
            boolean callerIsStudent) {
        ReportDataProvider provider = providers.get(type);
        if (provider == null) {
            throw new ValidationException("Report provider is not implemented for " + type.reportType());
        }
        return provider.project(new ReportCriteria(type, filters, callerId, callerIsStudent, Instant.now()));
    }

    /** A student report is always scoped to the caller; a query parameter cannot change the subject (§76). */
    private Map<String, String> scopedFilters(ReportType type, Map<String, String> filters, UUID callerId,
            boolean callerIsStudent) {
        Map<String, String> scoped = new LinkedHashMap<>(filters == null ? Map.of() : filters);
        if (callerIsStudent) {
            scoped.put("studentId", callerId.toString());
        }
        return scoped;
    }

    // ------------------------------------------------------------------ job status, history, download

    @Transactional(readOnly = true)
    public ReportingDtos.ReportJobStatus status(UUID jobId, UUID callerId, boolean canReadAny) {
        BackgroundJob job = requireReportJob(jobId);
        requireOwnership(job, callerId, canReadAny);
        ReportType type = ReportType.byCode(job.getReferenceType()).orElse(null);
        boolean completed = job.getStatus() == BackgroundJobStatus.COMPLETED;
        return new ReportingDtos.ReportJobStatus(job.getId(),
                job.getReferenceType(), job.getStatus().name(),
                type == null ? null : type.supportedFormats().stream().findFirst().map(Enum::name).orElse(null),
                job.getErrorCode(), job.getErrorMessage(),
                completed ? artifactName(type, Map.of(), currentFormat(job)) : null,
                completed ? downloadUrl(job.getId()) : null, job.getQueuedOn(), job.getCompletedOn());
    }

    @Transactional(readOnly = true)
    public List<ReportingDtos.ReportHistoryEntry> history(UUID callerId, boolean canReadAny, int limit) {
        return jobRepository.findByJobTypeOrderByQueuedOnDesc(JobTypes.REPORT_GENERATION, PageRequest.of(0, limit))
                .stream()
                .filter(job -> canReadAny || callerId.equals(job.getInitiatedBy()))
                .map(job -> new ReportingDtos.ReportHistoryEntry(job.getId(), job.getReferenceType(),
                        job.getInitiatedBy(), job.getQueuedOn(), job.getStatus().name(),
                        job.getStatus() == BackgroundJobStatus.COMPLETED ? downloadUrl(job.getId()) : null))
                .toList();
    }

    /** Reads a protected artifact after authorization; a path or storage key is never returned (§22). */
    @Transactional(readOnly = true)
    public Artifact read(UUID jobId, UUID callerId, boolean canReadAny, String token) {
        BackgroundJob job = requireReportJob(jobId);
        boolean tokenAuthorized = jobId.equals(artifactStore.verifyToken(token));
        if (!tokenAuthorized) {
            requireOwnership(job, callerId, canReadAny);
        }
        if (job.getStatus() != BackgroundJobStatus.COMPLETED || job.getResultReference() == null) {
            throw NotFoundException.of("Report artifact", jobId);
        }
        metrics.downloaded(true);
        ReportType type = ReportType.byCode(job.getReferenceType()).orElse(null);
        ReportFormat format = currentFormat(job);
        return new Artifact(artifactName(type, Map.of(), format), renderer.contentType(format),
                artifactStore.read(job.getResultReference()));
    }

    /** A protected artifact plus its safe presentation metadata. */
    public record Artifact(String fileName, String contentType, byte[] content) {
    }

    public String downloadUrl(UUID jobId) {
        return "/api/v1/reports/jobs/" + jobId + "/download";
    }

    /** A short-lived signed URL, scoped to the exact artifact, for a caller who may already read it. */
    public String signedDownloadUrl(UUID jobId) {
        return downloadUrl(jobId) + "?token=" + artifactStore.issueToken(jobId);
    }

    private BackgroundJob requireReportJob(UUID jobId) {
        BackgroundJob job = jobRepository.findById(jobId).orElseThrow(() -> NotFoundException.of("Report job", jobId));
        if (!JobTypes.REPORT_GENERATION.equals(job.getJobType())) {
            throw NotFoundException.of("Report job", jobId);
        }
        return job;
    }

    /** A caller reads only their own report job unless they hold an operational read permission (§27). */
    private void requireOwnership(BackgroundJob job, UUID callerId, boolean canReadAny) {
        if (!canReadAny && !callerId.equals(job.getInitiatedBy())) {
            metrics.downloaded(false);
            throw new AuthorizationException("That report belongs to another user");
        }
    }

    private ReportFormat currentFormat(BackgroundJob job) {
        try {
            ReportJobParameters parameters = objectMapper.readValue(job.getParameters(), ReportJobParameters.class);
            return ReportFormat.valueOf(parameters.format());
        } catch (RuntimeException unknown) {
            return ReportFormat.PDF;
        }
    }

    /** Deterministic and safe: derived from the report type, filters, and format — never a raw string (§32). */
    private String artifactName(ReportType type, Map<String, String> filters, ReportFormat format) {
        String base = type == null
                ? "report"
                : type.reportType().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        StringBuilder name = new StringBuilder(base);
        if (filters != null) {
            for (String key : List.of("from", "to")) {
                String value = filters.get(key);
                if (value != null && !value.isBlank()) {
                    name.append('-').append(value.replaceAll("[^0-9A-Za-z]", "-"));
                }
            }
        }
        return name + renderer.extension(format);
    }

    private ReportingDtos.ReportCatalogEntry toCatalogEntry(ReportType type) {
        return new ReportingDtos.ReportCatalogEntry(type.reportType(), type.category().name(), type.displayName(),
                type.description(), type.supportedFormats().stream().map(Enum::name).toList(), type.permission(),
                type.filterSchema(), type.executionMode().name(), type.enabled());
    }
}
