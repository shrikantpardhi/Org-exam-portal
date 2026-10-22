package com.company.exam.assessment.service;

import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.entity.Assessment;
import com.company.exam.assessment.entity.AssessmentVersion;
import com.company.exam.assessment.mapper.AssessmentMapper;
import com.company.exam.assessment.repository.AssessmentRepository;
import com.company.exam.assessment.repository.AssessmentVersionRepository;
import com.company.exam.assessment.validation.AssessmentTextNormalizer;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assessment logical CRUD ({@code specs/13-P4-Assessment-V1.5.md §64, §182}). */
@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final AssessmentVersionRepository versionRepository;
    private final AssessmentMapper mapper;
    private final AssessmentTextNormalizer normalizer;
    private final SecurityEventLogger audit;

    public AssessmentService(AssessmentRepository assessmentRepository, AssessmentVersionRepository versionRepository,
            AssessmentMapper mapper, AssessmentTextNormalizer normalizer, SecurityEventLogger audit) {
        this.assessmentRepository = assessmentRepository;
        this.versionRepository = versionRepository;
        this.mapper = mapper;
        this.normalizer = normalizer;
        this.audit = audit;
    }

    /**
     * Lists logical assessments. Filters {@code status}, {@code search}, {@code blueprintId} and
     * {@code examId} are supported ({@code specs/13-P4-Assessment-V1.5.md §64, §95}).
     *
     * <p>{@code examId} is resolved through the selection-rule provenance: an assessment matches when
     * one of its versions carries a selection rule scoped to that exam. The frozen {@code assessment}
     * table has no exam column, so there is no other link to filter on; the rule is the scope the
     * platform itself acts on when it materializes the question pool. The rule is recorded in
     * ADR-0027.
     */
    @Transactional(readOnly = true)
    public Page<AssessmentDtos.AssessmentListItem> list(Status status, UUID blueprintId, UUID examId, String search,
            Pageable pageable) {
        List<UUID> examScopedIds = examId == null ? null
                : assessmentRepository.findAssessmentIdsBySelectionRuleExamId(examId.toString());
        return assessmentRepository.findAll(specification(status, blueprintId, examScopedIds, search), pageable)
                .map(mapper::toListItem);
    }

    @Transactional(readOnly = true)
    public AssessmentDtos.AssessmentResponse get(UUID id) {
        Assessment assessment = require(id);
        return mapper.toResponse(assessment, versionRepository.findByAssessmentIdOrderByVersionAsc(id));
    }

    @Transactional
    public AssessmentDtos.AssessmentResponse create(AssessmentDtos.AssessmentCreateRequest request, UUID actorId) {
        String code = normalizer.normalizeCode(request.code(), 50);
        if (assessmentRepository.existsByCode(code)) {
            throw new ConflictException("An assessment with this code already exists");
        }
        Assessment assessment = new Assessment();
        assessment.setCode(code);
        assessment.setName(normalizer.normalizeName(request.name(), 200));
        assessment.setDescription(normalizer.normalizeOptional(request.description()));
        assessment.setStatus(Status.ACTIVE);
        Assessment saved = save(assessment);
        audit.audit(actorId, "CREATE", "Assessment", saved.getId(), "Created assessment " + code);
        return mapper.toResponse(saved, List.of());
    }

    @Transactional
    public AssessmentDtos.AssessmentResponse update(UUID id, AssessmentDtos.AssessmentUpdateRequest request,
            UUID actorId) {
        Assessment assessment = require(id);
        if (request.name() != null) {
            assessment.setName(normalizer.normalizeName(request.name(), 200));
        }
        if (request.description() != null) {
            assessment.setDescription(normalizer.normalizeOptional(request.description()));
        }
        if (request.status() != null) {
            if (request.status() == Status.ARCHIVED) {
                throw new InvalidStateException("Use the archive operation to archive an assessment");
            }
            assessment.setStatus(request.status());
        }
        Assessment saved = assessmentRepository.save(assessment);
        audit.audit(actorId, "UPDATE", "Assessment", saved.getId(), "Updated assessment metadata");
        return get(id);
    }

    @Transactional(readOnly = true)
    public Assessment require(UUID id) {
        return assessmentRepository.findById(id).orElseThrow(() -> NotFoundException.of("Assessment", id));
    }

    private Specification<Assessment> specification(Status status, UUID blueprintId, List<UUID> examScopedIds,
            String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like)));
            }
            if (blueprintId != null) {
                Subquery<UUID> sub = query.subquery(UUID.class);
                Root<AssessmentVersion> version = sub.from(AssessmentVersion.class);
                Root<com.company.exam.assessment.entity.BlueprintVersion> blueprintVersion =
                        sub.from(com.company.exam.assessment.entity.BlueprintVersion.class);
                sub.select(version.get("assessmentId")).where(
                        cb.equal(version.get("blueprintVersionId"), blueprintVersion.get("id")),
                        cb.equal(blueprintVersion.get("blueprintId"), blueprintId),
                        cb.equal(version.get("assessmentId"), root.get("id")));
                predicates.add(cb.exists(sub));
            }
            if (examScopedIds != null) {
                // An exam that no assessment is authored against matches nothing: an empty id set is
                // an empty result, never an error and never an unfiltered list.
                predicates.add(examScopedIds.isEmpty()
                        ? cb.disjunction()
                        : root.get("id").in(examScopedIds));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Assessment save(Assessment assessment) {
        try {
            return assessmentRepository.saveAndFlush(assessment);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("An assessment with this code already exists");
        }
    }
}
