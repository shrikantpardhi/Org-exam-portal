package com.company.exam.academic.service;

import com.company.exam.academic.dto.AcademicDtos;
import com.company.exam.academic.entity.Chapter;
import com.company.exam.academic.entity.Topic;
import com.company.exam.academic.mapper.AcademicMapper;
import com.company.exam.academic.repository.ChapterRepository;
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

/** Topic workflows ({@code specs/11-P2-Academic-V1.5.md §27.4}). */
@Service
public class TopicService {

    private final TopicRepository topicRepository;
    private final ChapterRepository chapterRepository;
    private final SubTopicRepository subTopicRepository;
    private final AcademicMapper mapper;
    private final AcademicValidator validator;
    private final SecurityEventLogger audit;

    public TopicService(TopicRepository topicRepository, ChapterRepository chapterRepository,
            SubTopicRepository subTopicRepository, AcademicMapper mapper, AcademicValidator validator,
            SecurityEventLogger audit) {
        this.topicRepository = topicRepository;
        this.chapterRepository = chapterRepository;
        this.subTopicRepository = subTopicRepository;
        this.mapper = mapper;
        this.validator = validator;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<AcademicDtos.TopicListItem> list(UUID chapterId, Status status, String search, Pageable pageable) {
        if (chapterId == null) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("chapterId", "REQUIRED_FILTER", "chapterId is required")));
        }
        Specification<Topic> specification = AcademicSpecifications.<Topic>parentEquals("chapterId", chapterId)
                .and(AcademicSpecifications.status(status))
                .and(AcademicSpecifications.search(search, "code", "name"));
        return topicRepository.findAll(specification, pageable).map(mapper::toListItem);
    }

    @Transactional(readOnly = true)
    public AcademicDtos.TopicResponse get(UUID id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public AcademicDtos.TopicResponse create(AcademicDtos.TopicCreateRequest request, UUID actorId) {
        Chapter chapter = chapterRepository.findById(request.chapterId())
                .orElseThrow(() -> NotFoundException.of("Chapter", request.chapterId()));
        validator.requireParentEligible(chapter.getStatus(), "chapter");
        String code = validator.normalizeCode(request.code(), 50);
        String name = validator.normalizeName(request.name(), 150);
        if (topicRepository.existsByChapterIdAndCode(request.chapterId(), code)) {
            throw new ConflictException("A topic with this code already exists in this chapter");
        }
        Topic topic = new Topic();
        topic.setChapterId(request.chapterId());
        topic.setCode(code);
        topic.setName(name);
        topic.setDescription(validator.normalizeDescription(request.description()));
        topic.setStatus(Status.ACTIVE);
        Topic saved = save(topic);
        audit.audit(actorId, "CREATE", "Topic", saved.getId(), "Created topic " + code);
        return mapper.toResponse(saved);
    }

    @Transactional
    public AcademicDtos.TopicResponse update(UUID id, AcademicDtos.TopicUpdateRequest request, UUID actorId) {
        Topic topic = require(id);
        if (request.name() != null) {
            topic.setName(validator.normalizeName(request.name(), 150));
        }
        if (request.description() != null) {
            topic.setDescription(validator.normalizeDescription(request.description()));
        }
        Status status = validator.validateLifecycleStatus(request.status());
        if (status != null) {
            topic.setStatus(status);
        }
        Topic saved = save(topic);
        audit.audit(actorId, "UPDATE", "Topic", saved.getId(), "Updated topic " + saved.getCode());
        return mapper.toResponse(saved);
    }

    @Transactional
    public void archive(UUID id, UUID actorId) {
        Topic topic = require(id);
        validator.requireNoActiveDescendants(
                subTopicRepository.countByTopicIdAndStatusNot(id, Status.ARCHIVED), "topic");
        topic.setStatus(Status.ARCHIVED);
        save(topic);
        audit.audit(actorId, "ARCHIVE", "Topic", id, "Archived topic " + topic.getCode());
    }

    private Topic require(UUID id) {
        return topicRepository.findById(id).orElseThrow(() -> NotFoundException.of("Topic", id));
    }

    private Topic save(Topic topic) {
        try {
            return topicRepository.saveAndFlush(topic);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A topic with this code already exists in this chapter");
        }
    }
}
