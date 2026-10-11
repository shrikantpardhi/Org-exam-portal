package com.company.exam.content.service;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.entity.QuestionTemplate;
import com.company.exam.content.mapper.ContentMapper;
import com.company.exam.content.repository.QuestionTemplateRepository;
import com.company.exam.content.validation.ContentTextNormalizer;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Question template workflows ({@code specs/12-P3-Content-V1.5.md §87, §124}).
 *
 * <p>Template configuration is master data. Per the P3 decision (§69, §70), configuration changes
 * are treated as safe backward-compatible updates; published versions are never reinterpreted
 * silently.
 */
@Service
public class QuestionTemplateService {

    private final QuestionTemplateRepository templateRepository;
    private final ContentMapper mapper;
    private final ContentTextNormalizer normalizer;
    private final SecurityEventLogger audit;

    public QuestionTemplateService(QuestionTemplateRepository templateRepository, ContentMapper mapper,
            ContentTextNormalizer normalizer, SecurityEventLogger audit) {
        this.templateRepository = templateRepository;
        this.mapper = mapper;
        this.normalizer = normalizer;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<ContentDtos.TemplateResponse> list(Pageable pageable) {
        return templateRepository.findAll(pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ContentDtos.TemplateResponse get(UUID id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public ContentDtos.TemplateResponse create(ContentDtos.TemplateCreateRequest request, UUID actorId) {
        String code = normalizer.normalizeCode(request.code(), 50);
        if (templateRepository.existsByCode(code)) {
            throw new ConflictException("A question template with this code already exists");
        }
        QuestionTemplate template = new QuestionTemplate();
        template.setCode(code);
        template.setName(normalizer.normalizeName(request.name(), 150));
        template.setDescription(normalizer.normalizeOptional(request.description()));
        template.setConfiguration(normalizer.normalizeConfiguration(request.configuration()));
        template.setStatus(Status.ACTIVE);
        QuestionTemplate saved = save(template);
        audit.audit(actorId, "CREATE", "QuestionTemplate", saved.getId(), "Created question template " + code);
        return mapper.toResponse(saved);
    }

    @Transactional
    public ContentDtos.TemplateResponse update(UUID id, ContentDtos.TemplateUpdateRequest request, UUID actorId) {
        QuestionTemplate template = require(id);
        if (request.name() != null) {
            template.setName(normalizer.normalizeName(request.name(), 150));
        }
        if (request.description() != null) {
            template.setDescription(normalizer.normalizeOptional(request.description()));
        }
        if (request.configuration() != null) {
            template.setConfiguration(normalizer.normalizeConfiguration(request.configuration()));
        }
        if (request.status() != null) {
            template.setStatus(request.status());
        }
        QuestionTemplate saved = save(template);
        audit.audit(actorId, "UPDATE", "QuestionTemplate", saved.getId(), "Updated question template " + saved.getCode());
        return mapper.toResponse(saved);
    }

    private QuestionTemplate require(UUID id) {
        return templateRepository.findById(id).orElseThrow(() -> NotFoundException.of("Question template", id));
    }

    private QuestionTemplate save(QuestionTemplate template) {
        try {
            return templateRepository.saveAndFlush(template);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A question template with this code already exists");
        }
    }

    /** Ensures a template exists for internal references. */
    @Transactional(readOnly = true)
    public QuestionTemplate requireTemplate(UUID id) {
        return require(id);
    }
}
