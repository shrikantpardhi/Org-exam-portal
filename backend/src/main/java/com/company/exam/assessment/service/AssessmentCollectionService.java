package com.company.exam.assessment.service;

import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.entity.AssessmentCollection;
import com.company.exam.assessment.entity.CollectionAssessment;
import com.company.exam.assessment.mapper.AssessmentMapper;
import com.company.exam.assessment.repository.AssessmentCollectionRepository;
import com.company.exam.assessment.repository.AssessmentRepository;
import com.company.exam.assessment.repository.CollectionAssessmentRepository;
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

/** Assessment collection workflows ({@code specs/13-P4-Assessment-V1.5.md §76, §77, §194}). */
@Service
public class AssessmentCollectionService {

    private final AssessmentCollectionRepository collectionRepository;
    private final CollectionAssessmentRepository membershipRepository;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentMapper mapper;
    private final AssessmentTextNormalizer normalizer;
    private final SecurityEventLogger audit;

    public AssessmentCollectionService(AssessmentCollectionRepository collectionRepository,
            CollectionAssessmentRepository membershipRepository, AssessmentRepository assessmentRepository,
            AssessmentMapper mapper, AssessmentTextNormalizer normalizer, SecurityEventLogger audit) {
        this.collectionRepository = collectionRepository;
        this.membershipRepository = membershipRepository;
        this.assessmentRepository = assessmentRepository;
        this.mapper = mapper;
        this.normalizer = normalizer;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<AssessmentDtos.CollectionResponse> list(Pageable pageable) {
        return collectionRepository.findAll(pageable) //
                .map(collection -> mapper.toResponse(collection, assessmentIds(collection.getId())));
    }

    @Transactional(readOnly = true)
    public AssessmentDtos.CollectionResponse get(UUID id) {
        return mapper.toResponse(require(id), assessmentIds(id));
    }

    @Transactional
    public AssessmentDtos.CollectionResponse create(AssessmentDtos.CollectionCreateRequest request, UUID actorId) {
        String code = normalizer.normalizeCode(request.code(), 50);
        if (collectionRepository.existsByCode(code)) {
            throw new ConflictException("A collection with this code already exists");
        }
        AssessmentCollection collection = new AssessmentCollection();
        collection.setCode(code);
        collection.setName(normalizer.normalizeName(request.name(), 200));
        collection.setDescription(normalizer.normalizeOptional(request.description()));
        collection.setStatus(Status.ACTIVE);
        AssessmentCollection saved = save(collection);
        audit.audit(actorId, "CREATE", "AssessmentCollection", saved.getId(), "Created collection " + code);
        return mapper.toResponse(saved, List.of());
    }

    @Transactional
    public AssessmentDtos.CollectionResponse update(UUID id, AssessmentDtos.CollectionUpdateRequest request, UUID actorId) {
        AssessmentCollection collection = require(id);
        if (request.name() != null) {
            collection.setName(normalizer.normalizeName(request.name(), 200));
        }
        if (request.description() != null) {
            collection.setDescription(normalizer.normalizeOptional(request.description()));
        }
        if (request.status() != null) {
            if (request.status() == Status.ARCHIVED) {
                throw new InvalidStateException("Use the archive operation to archive a collection");
            }
            collection.setStatus(request.status());
        }
        AssessmentCollection saved = collectionRepository.save(collection);
        audit.audit(actorId, "UPDATE", "AssessmentCollection", saved.getId(), "Updated collection metadata");
        return get(id);
    }

    @Transactional
    public AssessmentDtos.CollectionResponse addAssessment(UUID collectionId, UUID assessmentId, UUID actorId) {
        require(collectionId);
        if (!assessmentRepository.existsById(assessmentId)) {
            throw NotFoundException.of("Assessment", assessmentId);
        }
        if (membershipRepository.existsByCollectionIdAndAssessmentId(collectionId, assessmentId)) {
            throw new ConflictException("The assessment is already part of this collection");
        }
        CollectionAssessment membership = new CollectionAssessment();
        membership.setCollectionId(collectionId);
        membership.setAssessmentId(assessmentId);
        membership.setStatus(Status.ACTIVE);
        try {
            membershipRepository.saveAndFlush(membership);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("The assessment is already part of this collection");
        }
        audit.audit(actorId, "ADD_ASSESSMENT", "AssessmentCollection", collectionId, "Added assessment to collection");
        return get(collectionId);
    }

    @Transactional
    public void removeAssessment(UUID collectionId, UUID assessmentId, UUID actorId) {
        require(collectionId);
        CollectionAssessment membership = membershipRepository
                .findByCollectionIdAndAssessmentId(collectionId, assessmentId)
                .orElseThrow(() -> NotFoundException.of("Collection assessment", assessmentId));
        membershipRepository.delete(membership);
        audit.audit(actorId, "REMOVE_ASSESSMENT", "AssessmentCollection", collectionId,
                "Removed assessment from collection");
    }

    private List<UUID> assessmentIds(UUID collectionId) {
        return membershipRepository.findByCollectionId(collectionId).stream()
                .filter(membership -> membership.getStatus() == Status.ACTIVE)
                .map(CollectionAssessment::getAssessmentId)
                .toList();
    }

    private AssessmentCollection require(UUID id) {
        return collectionRepository.findById(id).orElseThrow(() -> NotFoundException.of("Assessment collection", id));
    }

    private AssessmentCollection save(AssessmentCollection collection) {
        try {
            return collectionRepository.saveAndFlush(collection);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A collection with this code already exists");
        }
    }
}
