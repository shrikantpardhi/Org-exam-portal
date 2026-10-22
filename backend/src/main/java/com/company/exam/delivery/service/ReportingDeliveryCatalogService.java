package com.company.exam.delivery.service;

import com.company.exam.delivery.api.ReportingDeliveryCatalog;
import com.company.exam.delivery.entity.StudentAttempt;
import com.company.exam.delivery.repository.StudentAttemptRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link ReportingDeliveryCatalog}
 * ({@code specs/22-P13-Reporting-V1.5.md §10, §39, §44, §47}).
 *
 * <p>The repository exposes attempt reads by user, by version set and by schedule. The date range is
 * applied here over that already-bounded set and the result is truncated to the caller's limit, so a
 * report can never load an unbounded attempt population ({@code §7.6}). Nothing is mutated
 * ({@code §7.3}).
 */
@Service
public class ReportingDeliveryCatalogService implements ReportingDeliveryCatalog {

    private final StudentAttemptRepository attemptRepository;

    public ReportingDeliveryCatalogService(StudentAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttemptRow> attemptsForUser(UUID userId, Instant from, Instant to, int limit) {
        if (userId == null) {
            return List.of();
        }
        return bound(attemptRepository.findByUserIdOrderByStartedOnDesc(userId), from, to, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttemptRow> attemptsForVersions(Collection<UUID> assessmentVersionIds, Instant from, Instant to,
            int limit) {
        if (assessmentVersionIds == null || assessmentVersionIds.isEmpty()) {
            return List.of();
        }
        return bound(attemptRepository.findByAssessmentVersionIdIn(List.copyOf(assessmentVersionIds)), from, to,
                limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttemptRow> attemptsForSchedule(UUID assessmentScheduleId, int limit) {
        if (assessmentScheduleId == null) {
            return List.of();
        }
        return bound(attemptRepository.findByAssessmentScheduleIdOrderByCreatedOnAsc(assessmentScheduleId), null,
                null, limit);
    }

    /**
     * Applies the inclusive date window to the attempt's start, orders deterministically by start
     * descending, and truncates to the limit.
     */
    private List<AttemptRow> bound(List<StudentAttempt> attempts, Instant from, Instant to, int limit) {
        List<AttemptRow> rows = new ArrayList<>();
        for (StudentAttempt attempt : attempts) {
            Instant startedOn = attempt.getStartedOn();
            if (from != null && (startedOn == null || startedOn.isBefore(from))) {
                continue;
            }
            if (to != null && (startedOn == null || startedOn.isAfter(to))) {
                continue;
            }
            rows.add(new AttemptRow(attempt.getId(), attempt.getUserId(), attempt.getAssessmentVersionId(),
                    attempt.getAssessmentScheduleId(),
                    attempt.getAttemptMode() == null ? null : attempt.getAttemptMode().name(),
                    attempt.getAttemptStatus() == null ? null : attempt.getAttemptStatus().name(), startedOn,
                    attempt.getSubmittedOn(), attempt.getTotalScore()));
        }
        rows.sort(Comparator.comparing(AttemptRow::startedOn,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return rows.size() > limit ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows);
    }
}
