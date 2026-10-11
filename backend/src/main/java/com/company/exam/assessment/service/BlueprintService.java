package com.company.exam.assessment.service;

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
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Blueprint logical CRUD ({@code specs/13-P4-Assessment-V1.5.md §65, §177}). */
@Service
public class BlueprintService {

    private final BlueprintRepository blueprintRepository;
    private final BlueprintVersionRepository versionRepository;
    private final AssessmentMapper mapper;
    private final AssessmentTextNormalizer normalizer;
    private final SecurityEventLogger audit;

    public BlueprintService(BlueprintRepository blueprintRepository, BlueprintVersionRepository versionRepository,
            AssessmentMapper mapper, AssessmentTextNormalizer normalizer, SecurityEventLogger audit) {
        this.blueprintRepository = blueprintRepository;
        this.versionRepository = versionRepository;
        this.mapper = mapper;
        this.normalizer = normalizer;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<AssessmentDtos.BlueprintListItem> list(Pageable pageable) {
        return blueprintRepository.findAll(pageable).map(mapper::toListItem);
    }

    @Transactional(readOnly = true)
    public AssessmentDtos.BlueprintResponse get(UUID id) {
        Blueprint blueprint = require(id);
        return mapper.toResponse(blueprint, versionRepository.findByBlueprintIdOrderByVersionAsc(id));
    }

    @Transactional
    public AssessmentDtos.BlueprintResponse create(AssessmentDtos.BlueprintCreateRequest request, UUID actorId) {
        String code = normalizer.normalizeCode(request.code(), 50);
        if (blueprintRepository.existsByCode(code)) {
            throw new ConflictException("A blueprint with this code already exists");
        }
        Blueprint blueprint = new Blueprint();
        blueprint.setCode(code);
        blueprint.setName(normalizer.normalizeName(request.name(), 150));
        blueprint.setDescription(normalizer.normalizeOptional(request.description()));
        blueprint.setStatus(Status.ACTIVE);
        Blueprint saved = save(blueprint);
        audit.audit(actorId, "CREATE", "Blueprint", saved.getId(), "Created blueprint " + code);
        return mapper.toResponse(saved, List.of());
    }

    @Transactional
    public AssessmentDtos.BlueprintResponse update(UUID id, AssessmentDtos.BlueprintUpdateRequest request,
            UUID actorId) {
        Blueprint blueprint = require(id);
        if (request.name() != null) {
            blueprint.setName(normalizer.normalizeName(request.name(), 150));
        }
        if (request.description() != null) {
            blueprint.setDescription(normalizer.normalizeOptional(request.description()));
        }
        if (request.status() != null) {
            if (request.status() == Status.ARCHIVED) {
                throw new InvalidStateException("Use the archive operation to archive a blueprint");
            }
            blueprint.setStatus(request.status());
        }
        Blueprint saved = blueprintRepository.save(blueprint);
        audit.audit(actorId, "UPDATE", "Blueprint", saved.getId(), "Updated blueprint metadata");
        return get(id);
    }

    @Transactional(readOnly = true)
    public Blueprint require(UUID id) {
        return blueprintRepository.findById(id).orElseThrow(() -> NotFoundException.of("Blueprint", id));
    }

    private Blueprint save(Blueprint blueprint) {
        try {
            return blueprintRepository.saveAndFlush(blueprint);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A blueprint with this code already exists");
        }
    }
}
