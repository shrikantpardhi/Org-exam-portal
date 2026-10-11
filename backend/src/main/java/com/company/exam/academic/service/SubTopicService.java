package com.company.exam.academic.service;

import com.company.exam.academic.dto.AcademicDtos;
import com.company.exam.academic.entity.SubTopic;
import com.company.exam.academic.entity.Topic;
import com.company.exam.academic.mapper.AcademicMapper;
import com.company.exam.academic.repository.SubTopicRepository;
import com.company.exam.academic.repository.TopicRepository;
import com.company.exam.academic.validation.AcademicSpecifications;
import com.company.exam.academic.validation.AcademicValidator;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SubTopic workflows ({@code specs/11-P2-Academic-V1.5.md §27.5}). */
@Service
public class SubTopicService {

    private final SubTopicRepository subTopicRepository;
    private final TopicRepository topicRepository;
    private final AcademicMapper mapper;
    private final AcademicValidator validator;
    private final SecurityEventLogger audit;

    public SubTopicService(SubTopicRepository subTopicRepository, TopicRepository topicRepository,
            AcademicMapper mapper, AcademicValidator validator, SecurityEventLogger audit) {
        this.subTopicRepository = subTopicRepository;
        this.topicRepository = topicRepository;
        this.mapper = mapper;
        this.validator = validator;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<AcademicDtos.SubTopicListItem> list(UUID topicId, Status status, String search, Pageable pageable) {
        if (topicId == null) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("topicId", "REQUIRED_FILTER", "topicId is required")));
        }
        Specification<SubTopic> specification = AcademicSpecifications.<SubTopic>parentEquals("topicId", topicId)
                .and(AcademicSpecifications.status(status))
                .and(AcademicSpecifications.search(search, "code", "name"));
        return subTopicRepository.findAll(specification, pageable).map(mapper::toListItem);
    }

    @Transactional(readOnly = true)
    public AcademicDtos.SubTopicResponse get(UUID id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public AcademicDtos.SubTopicResponse create(AcademicDtos.SubTopicCreateRequest request, UUID actorId) {
        Topic topic = topicRepository.findById(request.topicId())
                .orElseThrow(() -> NotFoundException.of("Topic", request.topicId()));
        validator.requireParentEligible(topic.getStatus(), "topic");
        String code = validator.normalizeCode(request.code(), 50);
        String name = validator.normalizeName(request.name(), 150);
        if (subTopicRepository.existsByTopicIdAndCode(request.topicId(), code)) {
            throw new ConflictException("A sub-topic with this code already exists in this topic");
        }
        SubTopic subTopic = new SubTopic();
        subTopic.setTopicId(request.topicId());
        subTopic.setCode(code);
        subTopic.setName(name);
        subTopic.setDescription(validator.normalizeDescription(request.description()));
        subTopic.setStatus(Status.ACTIVE);
        SubTopic saved = save(subTopic);
        audit.audit(actorId, "CREATE", "SubTopic", saved.getId(), "Created sub-topic " + code);
        return mapper.toResponse(saved);
    }

    @Transactional
    public AcademicDtos.SubTopicResponse update(UUID id, AcademicDtos.SubTopicUpdateRequest request, UUID actorId) {
        SubTopic subTopic = require(id);
        if (request.name() != null) {
            subTopic.setName(validator.normalizeName(request.name(), 150));
        }
        if (request.description() != null) {
            subTopic.setDescription(validator.normalizeDescription(request.description()));
        }
        Status status = validator.validateLifecycleStatus(request.status());
        if (status != null) {
            subTopic.setStatus(status);
        }
        SubTopic saved = save(subTopic);
        audit.audit(actorId, "UPDATE", "SubTopic", saved.getId(), "Updated sub-topic " + saved.getCode());
        return mapper.toResponse(saved);
    }

    @Transactional
    public void archive(UUID id, UUID actorId) {
        SubTopic subTopic = require(id);
        subTopic.setStatus(Status.ARCHIVED);
        save(subTopic);
        audit.audit(actorId, "ARCHIVE", "SubTopic", id, "Archived sub-topic " + subTopic.getCode());
    }

    private SubTopic require(UUID id) {
        return subTopicRepository.findById(id).orElseThrow(() -> NotFoundException.of("SubTopic", id));
    }

    private SubTopic save(SubTopic subTopic) {
        try {
            return subTopicRepository.saveAndFlush(subTopic);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A sub-topic with this code already exists in this topic");
        }
    }
}
