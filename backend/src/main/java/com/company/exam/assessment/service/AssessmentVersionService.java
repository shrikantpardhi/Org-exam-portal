package com.company.exam.assessment.service;

import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.entity.Assessment;
import com.company.exam.assessment.entity.AssessmentVersion;
import com.company.exam.assessment.entity.BlueprintVersion;
import com.company.exam.assessment.entity.EngineType;
import com.company.exam.assessment.mapper.AssessmentMapper;
import com.company.exam.assessment.repository.AssessmentRepository;
import com.company.exam.assessment.repository.AssessmentVersionRepository;
import com.company.exam.assessment.repository.BlueprintVersionRepository;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.delivery.api.DeliveryPolicyCatalog;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assessment version workflows ({@code specs/13-P4-Assessment-V1.5.md §23, §50, §66, §183}).
 *
 * <p>Engine type and delivery policy are immutable once the version is published; a change requires
 * a new version.
 */
@Service
public class AssessmentVersionService {

    private final AssessmentRepository assessmentRepository;
    private final AssessmentVersionRepository versionRepository;
    private final BlueprintVersionRepository blueprintVersionRepository;
    private final DeliveryPolicyCatalog deliveryPolicyCatalog;
    private final AssessmentMapper mapper;
    private final SecurityEventLogger audit;

    public AssessmentVersionService(AssessmentRepository assessmentRepository,
            AssessmentVersionRepository versionRepository, BlueprintVersionRepository blueprintVersionRepository,
            DeliveryPolicyCatalog deliveryPolicyCatalog, AssessmentMapper mapper, SecurityEventLogger audit) {
        this.assessmentRepository = assessmentRepository;
        this.versionRepository = versionRepository;
        this.blueprintVersionRepository = blueprintVersionRepository;
        this.deliveryPolicyCatalog = deliveryPolicyCatalog;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional
    public AssessmentDtos.AssessmentVersionResponse create(UUID assessmentId,
            AssessmentDtos.AssessmentVersionCreateRequest request, UUID actorId) {
        Assessment assessment = require(assessmentId);
        if (assessment.getStatus() == Status.ARCHIVED) {
            throw new InvalidStateException("Cannot add a version to an archived assessment");
        }
        BlueprintVersion blueprintVersion = blueprintVersionRepository.findById(request.blueprintVersionId())
                .orElseThrow(() -> NotFoundException.of("Blueprint version", request.blueprintVersionId()));
        validateEngine(request.engineType());
        if (request.deliveryPolicyId() != null && !deliveryPolicyCatalog.exists(request.deliveryPolicyId())) {
            throw new BusinessException("Delivery policy not found",
                    List.of(ApiError.of("deliveryPolicyId", "DELIVERY_POLICY_NOT_FOUND",
                            "Delivery policy does not exist")));
        }

        int nextVersion = versionRepository.findMaxVersion(assessmentId) + 1;
        AssessmentVersion version = new AssessmentVersion();
        version.setAssessmentId(assessmentId);
        version.setBlueprintVersionId(blueprintVersion.getId());
        version.setVersion(nextVersion);
        version.setDurationMinutes(request.durationMinutes());
        version.setEngineType(request.engineType());
        version.setDeliveryPolicyId(request.deliveryPolicyId());
        version.setPublished(false);
        version.setStatus(Status.ACTIVE);
        AssessmentVersion saved = save(version);
        audit.audit(actorId, "CREATE", "AssessmentVersion", saved.getId(),
                "Created assessment version " + nextVersion + " of " + assessment.getCode());
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AssessmentDtos.AssessmentVersionResponse> list(UUID assessmentId) {
        require(assessmentId);
        return versionRepository.findByAssessmentIdOrderByVersionAsc(assessmentId).stream()
                .map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AssessmentDtos.AssessmentVersionResponse get(UUID assessmentId, UUID versionId) {
        return mapper.toResponse(requireVersion(assessmentId, versionId));
    }

    /** Direct version read model ({@code GET /assessment-versions/{versionId}}). */
    @Transactional(readOnly = true)
    public AssessmentDtos.AssessmentVersionResponse get(UUID versionId) {
        AssessmentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> NotFoundException.of("Assessment version", versionId));
        return mapper.toResponse(version);
    }

    @Transactional
    public AssessmentDtos.AssessmentVersionResponse update(UUID assessmentId, UUID versionId,
            AssessmentDtos.AssessmentVersionUpdateRequest request, UUID actorId) {
        AssessmentVersion version = requireVersion(assessmentId, versionId);
        if (version.isPublished()) {
            throw new InvalidStateException(
                    "Published assessment versions are immutable; a change requires a new version");
        }
        if (request.engineType() != null) {
            validateEngine(request.engineType());
            version.setEngineType(request.engineType());
        }
        if (request.deliveryPolicyId() != null) {
            if (!deliveryPolicyCatalog.exists(request.deliveryPolicyId())) {
                throw new BusinessException("Delivery policy not found",
                        List.of(ApiError.of("deliveryPolicyId", "DELIVERY_POLICY_NOT_FOUND",
                                "Delivery policy does not exist")));
            }
            version.setDeliveryPolicyId(request.deliveryPolicyId());
        }
        if (request.durationMinutes() != null) {
            version.setDurationMinutes(request.durationMinutes());
        }
        AssessmentVersion saved = versionRepository.save(version);
        audit.audit(actorId, "UPDATE", "AssessmentVersion", saved.getId(), "Updated assessment version");
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AssessmentVersion requireVersion(UUID assessmentId, UUID versionId) {
        require(assessmentId);
        return versionRepository.findByIdAndAssessmentId(versionId, assessmentId)
                .orElseThrow(() -> NotFoundException.of("Assessment version", versionId));
    }

    public void validateEngine(EngineType engineType) {
        if (engineType == null) {
            return;
        }
        try {
            EngineType.valueOf(engineType.name());
        } catch (IllegalArgumentException unsupported) {
            throw new BusinessException("Unsupported exam engine",
                    List.of(ApiError.of("engineType", "UNSUPPORTED_EXAM_ENGINE", "Unsupported exam engine")));
        }
    }

    private Assessment require(UUID id) {
        return assessmentRepository.findById(id).orElseThrow(() -> NotFoundException.of("Assessment", id));
    }

    private AssessmentVersion save(AssessmentVersion version) {
        try {
            return versionRepository.saveAndFlush(version);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A version with this number already exists for the assessment");
        }
    }
}
