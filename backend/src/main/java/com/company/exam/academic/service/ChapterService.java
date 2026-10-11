package com.company.exam.academic.service;

import com.company.exam.academic.dto.AcademicDtos;
import com.company.exam.academic.entity.Chapter;
import com.company.exam.academic.entity.Subject;
import com.company.exam.academic.mapper.AcademicMapper;
import com.company.exam.academic.repository.ChapterRepository;
import com.company.exam.academic.repository.SubjectRepository;
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

/** Chapter workflows ({@code specs/11-P2-Academic-V1.5.md §27.3}). */
@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final SubjectRepository subjectRepository;
    private final TopicRepository topicRepository;
    private final AcademicMapper mapper;
    private final AcademicValidator validator;
    private final SecurityEventLogger audit;

    public ChapterService(ChapterRepository chapterRepository, SubjectRepository subjectRepository,
            TopicRepository topicRepository, AcademicMapper mapper, AcademicValidator validator,
            SecurityEventLogger audit) {
        this.chapterRepository = chapterRepository;
        this.subjectRepository = subjectRepository;
        this.topicRepository = topicRepository;
        this.mapper = mapper;
        this.validator = validator;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<AcademicDtos.ChapterListItem> list(UUID subjectId, Status status, String search, Pageable pageable) {
        if (subjectId == null) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("subjectId", "REQUIRED_FILTER", "subjectId is required")));
        }
        Specification<Chapter> specification = AcademicSpecifications.<Chapter>parentEquals("subjectId", subjectId)
                .and(AcademicSpecifications.status(status))
                .and(AcademicSpecifications.search(search, "code", "name"));
        return chapterRepository.findAll(specification, pageable).map(mapper::toListItem);
    }

    @Transactional(readOnly = true)
    public AcademicDtos.ChapterResponse get(UUID id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public AcademicDtos.ChapterResponse create(AcademicDtos.ChapterCreateRequest request, UUID actorId) {
        Subject subject = subjectRepository.findById(request.subjectId())
                .orElseThrow(() -> NotFoundException.of("Subject", request.subjectId()));
        validator.requireParentEligible(subject.getStatus(), "subject");
        String code = validator.normalizeCode(request.code(), 50);
        String name = validator.normalizeName(request.name(), 150);
        if (chapterRepository.existsBySubjectIdAndCode(request.subjectId(), code)) {
            throw new ConflictException("A chapter with this code already exists in this subject");
        }
        Chapter chapter = new Chapter();
        chapter.setSubjectId(request.subjectId());
        chapter.setCode(code);
        chapter.setName(name);
        chapter.setDescription(validator.normalizeDescription(request.description()));
        chapter.setStatus(Status.ACTIVE);
        Chapter saved = save(chapter);
        audit.audit(actorId, "CREATE", "Chapter", saved.getId(), "Created chapter " + code);
        return mapper.toResponse(saved);
    }

    @Transactional
    public AcademicDtos.ChapterResponse update(UUID id, AcademicDtos.ChapterUpdateRequest request, UUID actorId) {
        Chapter chapter = require(id);
        if (request.name() != null) {
            chapter.setName(validator.normalizeName(request.name(), 150));
        }
        if (request.description() != null) {
            chapter.setDescription(validator.normalizeDescription(request.description()));
        }
        Status status = validator.validateLifecycleStatus(request.status());
        if (status != null) {
            chapter.setStatus(status);
        }
        Chapter saved = save(chapter);
        audit.audit(actorId, "UPDATE", "Chapter", saved.getId(), "Updated chapter " + saved.getCode());
        return mapper.toResponse(saved);
    }

    @Transactional
    public void archive(UUID id, UUID actorId) {
        Chapter chapter = require(id);
        validator.requireNoActiveDescendants(
                topicRepository.countByChapterIdAndStatusNot(id, Status.ARCHIVED), "chapter");
        chapter.setStatus(Status.ARCHIVED);
        save(chapter);
        audit.audit(actorId, "ARCHIVE", "Chapter", id, "Archived chapter " + chapter.getCode());
    }

    private Chapter require(UUID id) {
        return chapterRepository.findById(id).orElseThrow(() -> NotFoundException.of("Chapter", id));
    }

    private Chapter save(Chapter chapter) {
        try {
            return chapterRepository.saveAndFlush(chapter);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A chapter with this code already exists in this subject");
        }
    }
}
