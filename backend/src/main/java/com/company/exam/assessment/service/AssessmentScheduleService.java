package com.company.exam.assessment.service;

import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.entity.AssessmentSchedule;
import com.company.exam.assessment.entity.AssessmentVersion;
import com.company.exam.assessment.mapper.AssessmentMapper;
import com.company.exam.assessment.repository.AssessmentScheduleRepository;
import com.company.exam.assessment.repository.AssessmentVersionRepository;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schedule workflows ({@code specs/13-P4-Assessment-V1.5.md §67-§72, §193}).
 *
 * <p>A schedule binds a published AssessmentVersion to a time window and never follows a later
 * version. Mode is fixed at creation.
 */
@Service
public class AssessmentScheduleService {

    private final AssessmentScheduleRepository scheduleRepository;
    private final AssessmentVersionRepository versionRepository;
    private final AssessmentMapper mapper;
    private final SecurityEventLogger audit;
    private final org.springframework.context.ApplicationEventPublisher events;

    public AssessmentScheduleService(AssessmentScheduleRepository scheduleRepository,
            AssessmentVersionRepository versionRepository, AssessmentMapper mapper, SecurityEventLogger audit,
            org.springframework.context.ApplicationEventPublisher events) {
        this.scheduleRepository = scheduleRepository;
        this.versionRepository = versionRepository;
        this.mapper = mapper;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<AssessmentDtos.ScheduleResponse> list(UUID assessmentVersionId, Status status, Instant from,
            Instant to, Pageable pageable) {
        return scheduleRepository.findAll(specification(assessmentVersionId, status, from, to), pageable)
                .map(mapper::toResponse);
    }

    @Transactional
    public AssessmentDtos.ScheduleResponse create(AssessmentDtos.ScheduleCreateRequest request, UUID actorId) {
        AssessmentVersion version = versionRepository.findById(request.assessmentVersionId())
                .orElseThrow(() -> NotFoundException.of("Assessment version", request.assessmentVersionId()));
        if (!version.isPublished()) {
            throw new BusinessException("Assessment version is not published",
                    List.of(ApiError.of("assessmentVersionId", "ASSESSMENT_VERSION_INVALID",
                            "A schedule requires a published assessment version")));
        }
        validateWindow(request.startTime(), request.endTime());
        if (request.mode() == null) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("mode", "SCHEDULE_MODE_INVALID", "A schedule mode is required")));
        }
        AssessmentSchedule schedule = new AssessmentSchedule();
        schedule.setAssessmentVersionId(request.assessmentVersionId());
        schedule.setMode(request.mode());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setStatus(Status.ACTIVE);
        AssessmentSchedule saved = scheduleRepository.save(schedule);
        audit.audit(actorId, "CREATE", "AssessmentSchedule", saved.getId(), "Created assessment schedule");
        // Producer-owned business event; Communication consumes it after commit (§54, §115).
        events.publishEvent(new com.company.exam.common.events.BusinessEvents.AssessmentScheduledEvent(
                saved.getId(), saved.getAssessmentVersionId(), saved.getStartTime()));
        return mapper.toResponse(saved);
    }

    @Transactional
    public AssessmentDtos.ScheduleResponse update(UUID id, AssessmentDtos.ScheduleUpdateRequest request, UUID actorId) {
        AssessmentSchedule schedule = require(id);
        validateWindow(request.startTime(), request.endTime());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        AssessmentSchedule saved = scheduleRepository.save(schedule);
        audit.audit(actorId, "UPDATE", "AssessmentSchedule", saved.getId(), "Updated assessment schedule");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AssessmentDtos.ScheduleResponse close(UUID id, UUID actorId) {
        AssessmentSchedule schedule = require(id);
        // Closing disables the schedule; it does not unpublish the assessment version.
        schedule.setStatus(Status.INACTIVE);
        AssessmentSchedule saved = scheduleRepository.save(schedule);
        audit.audit(actorId, "CLOSE", "AssessmentSchedule", saved.getId(), "Closed assessment schedule");
        return mapper.toResponse(saved);
    }

    private void validateWindow(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("startTime", "SCHEDULE_WINDOW_INVALID",
                            "startTime must be present and before endTime")));
        }
    }

    private AssessmentSchedule require(UUID id) {
        return scheduleRepository.findById(id).orElseThrow(() -> NotFoundException.of("Assessment schedule", id));
    }

    private Specification<AssessmentSchedule> specification(UUID assessmentVersionId, Status status, Instant from,
            Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (assessmentVersionId != null) {
                predicates.add(cb.equal(root.get("assessmentVersionId"), assessmentVersionId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endTime"), to));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
