package com.company.exam.evaluation.service;

import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.common.api.PageResponse;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.delivery.api.AttemptEvaluationCatalog;
import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.entity.AnswerKey;
import com.company.exam.evaluation.entity.Evaluation;
import com.company.exam.evaluation.entity.Result;
import com.company.exam.evaluation.entity.ResultStatus;
import com.company.exam.evaluation.mapper.EvaluationMapper;
import com.company.exam.evaluation.repository.AnswerKeyRepository;
import com.company.exam.evaluation.repository.EvaluationRepository;
import com.company.exam.evaluation.repository.ResultRepository;
import com.company.exam.identity.api.UserDirectory;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Result visibility, listing, and rankings ({@code specs/15-P6-Evaluation-V1.5.md §39, §90, §95-§99, §101, §127}).
 *
 * <p>Students see only their own PUBLISHED results; unpublished or superseded state is never leaked
 * ({@code §87, §95}). Staff listing filters and paginates on the database side; the assessment and
 * student filters are resolved through application contracts into a bounded evaluation-id set so no
 * cross-module repository is touched ({@code §64, §96, §127}).
 */
@Service
public class ResultService {

    private final ResultRepository resultRepository;
    private final EvaluationRepository evaluationRepository;
    private final AnswerKeyRepository answerKeyRepository;
    private final AttemptEvaluationCatalog delivery;
    private final AssessmentDefinitionCatalog assessment;
    private final UserDirectory userDirectory;
    private final EvaluationMapper mapper;

    public ResultService(ResultRepository resultRepository, EvaluationRepository evaluationRepository,
            AnswerKeyRepository answerKeyRepository, AttemptEvaluationCatalog delivery,
            AssessmentDefinitionCatalog assessment, UserDirectory userDirectory, EvaluationMapper mapper) {
        this.resultRepository = resultRepository;
        this.evaluationRepository = evaluationRepository;
        this.answerKeyRepository = answerKeyRepository;
        this.delivery = delivery;
        this.assessment = assessment;
        this.userDirectory = userDirectory;
        this.mapper = mapper;
    }

    /** A single result, restricted for students to their own published result ({@code §95}). */
    @Transactional(readOnly = true)
    public EvaluationDtos.ResultResponse getVisible(UUID resultId, UUID userId, boolean staff) {
        Result result = resultRepository.findById(resultId)
                .orElseThrow(() -> NotFoundException.of("Result", resultId));
        Evaluation evaluation = evaluationRepository.findById(result.getEvaluationId())
                .orElseThrow(() -> NotFoundException.of("Evaluation", result.getEvaluationId()));
        if (!staff) {
            requireOwnedPublished(result, evaluation, userId);
        }
        return describe(result, evaluation);
    }

    /** The current result of an attempt ({@code §95}). */
    @Transactional(readOnly = true)
    public EvaluationDtos.ResultResponse getForAttempt(UUID attemptId, UUID userId, boolean staff) {
        AttemptEvaluationCatalog.AttemptView attempt = delivery.requireAttempt(attemptId);
        if (!staff && !attempt.userId().equals(userId)) {
            throw NotFoundException.of("Result for attempt", attemptId);
        }
        Evaluation evaluation = evaluationRepository.findByAttemptIdAndSupersededByIdIsNull(attemptId)
                .orElseThrow(() -> NotFoundException.of("Result for attempt", attemptId));
        Result result = resultRepository.findByEvaluationId(evaluation.getId())
                .orElseThrow(() -> NotFoundException.of("Result for attempt", attemptId));
        if (!staff && result.getResultStatus() != ResultStatus.PUBLISHED) {
            throw NotFoundException.of("Result for attempt", attemptId);
        }
        return describe(result, evaluation);
    }

    /** Database-side filtered, sorted, paginated staff result list ({@code §96, §127}). */
    @Transactional(readOnly = true)
    public PageResponse<EvaluationDtos.ResultResponse> list(UUID assessmentId, UUID assessmentVersionId,
            UUID studentId, String status, Instant from, Instant to, Pageable pageable) {
        Collection<UUID> evaluationIds = resolveEvaluationIds(assessmentId, assessmentVersionId, studentId);
        ResultStatus resultStatus = parseStatus(status);
        Specification<Result> specification = specification(evaluationIds, resultStatus, from, to);
        Page<Result> page = resultRepository.findAll(specification, pageable);
        return PageResponse.from(page, this::describe);
    }

    /** Rankings for an assessment (or one of its versions), masked for non-staff ({@code §99}). */
    @Transactional(readOnly = true)
    public PageResponse<EvaluationDtos.RankingsEntry> rankings(UUID assessmentId, UUID assessmentVersionId,
            UUID scheduleId, boolean staff, Pageable pageable) {
        List<UUID> versionIds = assessmentVersionId != null ? List.of(assessmentVersionId)
                : (assessmentId != null ? assessment.assessmentVersionIds(assessmentId) : List.of());
        if (versionIds.isEmpty()) {
            return new PageResponse<>(List.of(), com.company.exam.common.api.Pagination.from(
                    new PageImpl<>(List.of(), pageable, 0)));
        }
        List<Result> results = versionIds.stream()
                .flatMap(versionId -> resultRepository.findPublishedForVersion(versionId, ResultStatus.PUBLISHED).stream())
                .toList();
        if (results.isEmpty()) {
            return new PageResponse<>(List.of(), com.company.exam.common.api.Pagination.from(
                    new PageImpl<>(List.of(), pageable, 0)));
        }

        Map<UUID, Evaluation> evaluations = evaluationRepository
                .findAllById(results.stream().map(Result::getEvaluationId).distinct().toList()).stream()
                .collect(Collectors.toMap(Evaluation::getId, Function.identity()));
        Map<UUID, AttemptEvaluationCatalog.AttemptView> attempts = delivery
                .attempts(evaluations.values().stream().map(Evaluation::getAttemptId).distinct().toList()).stream()
                .collect(Collectors.toMap(AttemptEvaluationCatalog.AttemptView::attemptId, Function.identity()));

        List<EvaluationDtos.RankingsEntry> entries = results.stream()
                .map(result -> toEntry(result, evaluations, attempts, scheduleId, staff))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator
                        .comparing(EvaluationDtos.RankingsEntry::rank,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(EvaluationDtos.RankingsEntry::obtainedMarks,
                                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();

        int total = entries.size();
        int start = Math.min((int) pageable.getOffset(), total);
        int end = Math.min(start + pageable.getPageSize(), total);
        return new PageResponse<>(entries.subList(start, end),
                com.company.exam.common.api.Pagination.from(new PageImpl<>(entries, pageable, total)));
    }

    private EvaluationDtos.RankingsEntry toEntry(Result result, Map<UUID, Evaluation> evaluations,
            Map<UUID, AttemptEvaluationCatalog.AttemptView> attempts, UUID scheduleId, boolean staff) {
        Evaluation evaluation = evaluations.get(result.getEvaluationId());
        if (evaluation == null) {
            return null;
        }
        AttemptEvaluationCatalog.AttemptView attempt = attempts.get(evaluation.getAttemptId());
        if (attempt == null || (scheduleId != null && !scheduleId.equals(attempt.assessmentScheduleId()))) {
            return null;
        }
        if (staff) {
            return new EvaluationDtos.RankingsEntry(result.getRank(), attempt.userId(), displayName(attempt.userId()),
                    result.getObtainedMarks(), result.getPercentile());
        }
        return new EvaluationDtos.RankingsEntry(result.getRank(), null,
                mask(displayName(attempt.userId())), result.getObtainedMarks(), result.getPercentile());
    }

    private String displayName(UUID userId) {
        return userDirectory.find(userId)
                .map(user -> join(user.firstName(), user.lastName()))
                .orElse("");
    }

    private String join(String first, String last) {
        String value = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return value.isEmpty() ? "" : value;
    }

    /** Masks each name token as its initial plus a fixed filler ({@code §99}). */
    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        StringBuilder masked = new StringBuilder();
        for (String token : value.split("\\s+")) {
            if (masked.length() > 0) {
                masked.append(' ');
            }
            masked.append(token.charAt(0)).append("***");
        }
        return masked.toString();
    }

    private void requireOwnedPublished(Result result, Evaluation evaluation, UUID userId) {
        if (result.getResultStatus() != ResultStatus.PUBLISHED) {
            throw NotFoundException.of("Result", result.getId());
        }
        AttemptEvaluationCatalog.AttemptView attempt = delivery.requireAttempt(evaluation.getAttemptId());
        if (!attempt.userId().equals(userId)) {
            throw NotFoundException.of("Result", result.getId());
        }
    }

    private EvaluationDtos.ResultResponse describe(Result result) {
        Evaluation evaluation = evaluationRepository.findById(result.getEvaluationId()).orElse(null);
        return describe(result, evaluation);
    }

    private EvaluationDtos.ResultResponse describe(Result result, Evaluation evaluation) {
        UUID attemptId = evaluation == null ? null : evaluation.getAttemptId();
        UUID assessmentVersionId = null;
        if (evaluation != null) {
            assessmentVersionId = answerKeyRepository.findById(evaluation.getAnswerKeyId())
                    .map(AnswerKey::getAssessmentVersionId)
                    .orElse(null);
        }
        return mapper.toResponse(result, attemptId, assessmentVersionId);
    }

    private Collection<UUID> resolveEvaluationIds(UUID assessmentId, UUID assessmentVersionId, UUID studentId) {
        Collection<UUID> evaluationIds = null;
        if (assessmentVersionId != null || assessmentId != null) {
            List<UUID> versionIds = assessmentVersionId != null ? List.of(assessmentVersionId)
                    : assessment.assessmentVersionIds(assessmentId);
            if (versionIds.isEmpty()) {
                return List.of();
            }
            Set<UUID> keyIds = new HashSet<>(answerKeyRepository.findIdsByAssessmentVersionIdIn(versionIds));
            if (keyIds.isEmpty()) {
                return List.of();
            }
            evaluationIds = evaluationRepository.findIdsByAnswerKeyIdIn(keyIds);
            if (evaluationIds.isEmpty()) {
                return List.of();
            }
        }
        if (studentId != null) {
            List<UUID> attemptIds = delivery.attemptIdsForUser(studentId);
            if (attemptIds.isEmpty()) {
                return List.of();
            }
            Set<UUID> attemptEvaluations = new HashSet<>(evaluationRepository.findIdsByAttemptIdIn(attemptIds));
            evaluationIds = evaluationIds == null ? new ArrayList<>(attemptEvaluations)
                    : evaluationIds.stream().filter(attemptEvaluations::contains).toList();
        }
        return evaluationIds;
    }

    private Specification<Result> specification(Collection<UUID> evaluationIds, ResultStatus status, Instant from,
            Instant to) {
        return (root, query, cb) -> {
            if (evaluationIds != null && evaluationIds.isEmpty()) {
                return cb.disjunction();
            }
            List<Predicate> predicates = new ArrayList<>();
            if (evaluationIds != null) {
                predicates.add(root.get("evaluationId").in(evaluationIds));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("resultStatus"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdOn"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdOn"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ResultStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ResultStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new com.company.exam.common.exception.ValidationException("Invalid result status",
                    List.of(com.company.exam.common.api.ApiError.of("status", "INVALID_RESULT_STATUS",
                            "Unknown result status")));
        }
    }
}
