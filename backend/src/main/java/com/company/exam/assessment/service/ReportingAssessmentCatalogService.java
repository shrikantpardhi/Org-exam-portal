package com.company.exam.assessment.service;

import com.company.exam.assessment.api.ReportingAssessmentCatalog;
import com.company.exam.assessment.entity.Assessment;
import com.company.exam.assessment.entity.AssessmentSchedule;
import com.company.exam.assessment.entity.AssessmentVersion;
import com.company.exam.assessment.repository.AssessmentRepository;
import com.company.exam.assessment.repository.AssessmentScheduleRepository;
import com.company.exam.assessment.repository.AssessmentVersionRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link ReportingAssessmentCatalog}
 * ({@code specs/22-P13-Reporting-V1.5.md §10, §43-§47}).
 *
 * <p>The assessment list is truncated to the caller's limit; version and schedule reads are scoped to
 * an explicit id set, so no report scans the whole assessment catalogue ({@code §7.6}). Nothing is
 * published, edited or mutated here ({@code §7.3}).
 */
@Service
public class ReportingAssessmentCatalogService implements ReportingAssessmentCatalog {

    private final AssessmentRepository assessmentRepository;
    private final AssessmentVersionRepository versionRepository;
    private final AssessmentScheduleRepository scheduleRepository;

    public ReportingAssessmentCatalogService(AssessmentRepository assessmentRepository,
            AssessmentVersionRepository versionRepository, AssessmentScheduleRepository scheduleRepository) {
        this.assessmentRepository = assessmentRepository;
        this.versionRepository = versionRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentRow> assessments(int limit) {
        return assessmentRepository.findAll().stream()
                .limit(limit)
                .map(assessment -> new AssessmentRow(assessment.getId(), assessment.getCode(),
                        assessment.getName(),
                        assessment.getStatus() == null ? null : assessment.getStatus().name()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VersionRow> versionsOf(Collection<UUID> assessmentIds) {
        if (assessmentIds == null || assessmentIds.isEmpty()) {
            return List.of();
        }
        List<VersionRow> rows = new ArrayList<>();
        for (UUID assessmentId : assessmentIds) {
            for (AssessmentVersion version : versionRepository.findByAssessmentIdOrderByVersionAsc(assessmentId)) {
                rows.add(new VersionRow(version.getId(), version.getAssessmentId(), version.getVersion(),
                        version.getTotalQuestions(), version.getDurationMinutes(), version.isPublished()));
            }
        }
        return List.copyOf(rows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleRow> schedulesForVersions(Collection<UUID> assessmentVersionIds) {
        if (assessmentVersionIds == null || assessmentVersionIds.isEmpty()) {
            return List.of();
        }
        List<ScheduleRow> rows = new ArrayList<>();
        for (UUID versionId : assessmentVersionIds) {
            for (AssessmentSchedule schedule : scheduleRepository.findByAssessmentVersionId(versionId)) {
                rows.add(new ScheduleRow(schedule.getId(), schedule.getAssessmentVersionId(),
                        schedule.getMode() == null ? null : schedule.getMode().name(), schedule.getStartTime(),
                        schedule.getEndTime(),
                        schedule.getStatus() == null ? null : schedule.getStatus().name()));
            }
        }
        return List.copyOf(rows);
    }
}
