package com.company.exam.assessment.service;

import com.company.exam.assessment.blueprint.BlueprintConfigurationParser;
import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.entity.Blueprint;
import com.company.exam.assessment.entity.BlueprintVersion;
import com.company.exam.assessment.mapper.AssessmentMapper;
import com.company.exam.assessment.repository.BlueprintRepository;
import com.company.exam.assessment.repository.BlueprintVersionRepository;
import com.company.exam.assessment.validation.AssessmentTextNormalizer;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Blueprint version workflows ({@code §5.2, §22, §178}). */
@Service
public class BlueprintVersionService {

    private final BlueprintRepository blueprintRepository;
    private final BlueprintVersionRepository versionRepository;
    private final BlueprintConfigurationParser configurationParser;
    private final AssessmentMapper mapper;
    private final AssessmentTextNormalizer normalizer;
    private final SecurityEventLogger audit;

    public BlueprintVersionService(BlueprintRepository blueprintRepository,
            BlueprintVersionRepository versionRepository, BlueprintConfigurationParser configurationParser,
            AssessmentMapper mapper, AssessmentTextNormalizer normalizer, SecurityEventLogger audit) {
        this.blueprintRepository = blueprintRepository;
        this.versionRepository = versionRepository;
        this.configurationParser = configurationParser;
        this.mapper = mapper;
        this.normalizer = normalizer;
        this.audit = audit;
    }

    @Transactional
    public AssessmentDtos.BlueprintVersionResponse create(UUID blueprintId,
            AssessmentDtos.BlueprintVersionCreateRequest request, UUID actorId) {
        Blueprint blueprint = require(blueprintId);
        if (blueprint.getStatus() == Status.ARCHIVED) {
            throw new com.company.exam.common.exception.InvalidStateException(
                    "Cannot add a version to an archived blueprint");
        }
        String configuration = request.configuration().toString();
        configurationParser.parse(configuration);

        int nextVersion = versionRepository.findMaxVersion(blueprintId) + 1;
        BlueprintVersion version = new BlueprintVersion();
        version.setBlueprintId(blueprintId);
        version.setVersion(nextVersion);
        version.setConfiguration(configuration);
        version.setPublished(false);
        version.setStatus(Status.ACTIVE);
        BlueprintVersion saved = save(version);
        audit.audit(actorId, "CREATE", "BlueprintVersion", saved.getId(),
                "Created blueprint version " + nextVersion + " of " + blueprint.getCode());
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AssessmentDtos.BlueprintVersionResponse> list(UUID blueprintId) {
        require(blueprintId);
        return versionRepository.findByBlueprintIdOrderByVersionAsc(blueprintId).stream()
                .map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AssessmentDtos.BlueprintVersionResponse get(UUID blueprintId, UUID versionId) {
        return mapper.toResponse(requireVersion(blueprintId, versionId));
    }

    @Transactional
    public AssessmentDtos.BlueprintVersionResponse update(UUID blueprintId, UUID versionId,
            AssessmentDtos.BlueprintVersionUpdateRequest request, UUID actorId) {
        BlueprintVersion version = requireVersion(blueprintId, versionId);
        if (version.isPublished()) {
            throw new com.company.exam.common.exception.InvalidStateException(
                    "Published blueprint versions are immutable; create a new version");
        }
        String configuration = request.configuration().toString();
        configurationParser.parse(configuration);
        version.setConfiguration(configuration);
        BlueprintVersion saved = versionRepository.save(version);
        audit.audit(actorId, "UPDATE", "BlueprintVersion", saved.getId(), "Updated blueprint version configuration");
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BlueprintVersion requireVersion(UUID blueprintId, UUID versionId) {
        require(blueprintId);
        return versionRepository.findByIdAndBlueprintId(versionId, blueprintId)
                .orElseThrow(() -> NotFoundException.of("Blueprint version", versionId));
    }

    private Blueprint require(UUID id) {
        return blueprintRepository.findById(id).orElseThrow(() -> NotFoundException.of("Blueprint", id));
    }

    private BlueprintVersion save(BlueprintVersion version) {
        try {
            return versionRepository.saveAndFlush(version);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A version with this number already exists for the blueprint");
        }
    }
}
