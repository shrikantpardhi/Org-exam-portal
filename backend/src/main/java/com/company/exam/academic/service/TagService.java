package com.company.exam.academic.service;

import com.company.exam.academic.dto.AcademicDtos;
import com.company.exam.academic.entity.Tag;
import com.company.exam.academic.mapper.AcademicMapper;
import com.company.exam.academic.repository.TagRepository;
import com.company.exam.academic.validation.AcademicSpecifications;
import com.company.exam.academic.validation.AcademicValidator;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.NotFoundException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tag master workflows ({@code specs/11-P2-Academic-V1.5.md §27.7}).
 *
 * <p>Tag owns no QuestionTag junction; that belongs to Content.
 */
@Service
public class TagService {

    private final TagRepository tagRepository;
    private final AcademicMapper mapper;
    private final AcademicValidator validator;
    private final SecurityEventLogger audit;

    public TagService(TagRepository tagRepository, AcademicMapper mapper, AcademicValidator validator,
            SecurityEventLogger audit) {
        this.tagRepository = tagRepository;
        this.mapper = mapper;
        this.validator = validator;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<AcademicDtos.TagListItem> list(Status status, String search, Pageable pageable) {
        Specification<Tag> specification = AcademicSpecifications.<Tag>status(status)
                .and(AcademicSpecifications.search(search, "code", "name"));
        return tagRepository.findAll(specification, pageable).map(mapper::toListItem);
    }

    @Transactional(readOnly = true)
    public AcademicDtos.TagResponse get(UUID id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public AcademicDtos.TagResponse create(AcademicDtos.TagCreateRequest request, UUID actorId) {
        String code = validator.normalizeCode(request.code(), 50);
        String name = validator.normalizeName(request.name(), 100);
        if (tagRepository.existsByCode(code)) {
            throw new ConflictException("A tag with this code already exists");
        }
        Tag tag = new Tag();
        tag.setCode(code);
        tag.setName(name);
        tag.setDescription(validator.normalizeDescription(request.description()));
        tag.setStatus(Status.ACTIVE);
        Tag saved = save(tag);
        audit.audit(actorId, "CREATE", "Tag", saved.getId(), "Created tag " + code);
        return mapper.toResponse(saved);
    }

    @Transactional
    public AcademicDtos.TagResponse update(UUID id, AcademicDtos.TagUpdateRequest request, UUID actorId) {
        Tag tag = require(id);
        if (request.name() != null) {
            tag.setName(validator.normalizeName(request.name(), 100));
        }
        if (request.description() != null) {
            tag.setDescription(validator.normalizeDescription(request.description()));
        }
        Status status = validator.validateLifecycleStatus(request.status());
        if (status != null) {
            tag.setStatus(status);
        }
        Tag saved = save(tag);
        audit.audit(actorId, "UPDATE", "Tag", saved.getId(), "Updated tag " + saved.getCode());
        return mapper.toResponse(saved);
    }

    @Transactional
    public void archive(UUID id, UUID actorId) {
        Tag tag = require(id);
        tag.setStatus(Status.ARCHIVED);
        save(tag);
        audit.audit(actorId, "ARCHIVE", "Tag", id, "Archived tag " + tag.getCode());
    }

    private Tag require(UUID id) {
        return tagRepository.findById(id).orElseThrow(() -> NotFoundException.of("Tag", id));
    }

    private Tag save(Tag tag) {
        try {
            return tagRepository.saveAndFlush(tag);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A tag with this code already exists");
        }
    }
}
