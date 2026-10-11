package com.company.exam.academic.service;

import com.company.exam.academic.dto.AcademicDtos;
import com.company.exam.academic.entity.Language;
import com.company.exam.academic.mapper.AcademicMapper;
import com.company.exam.academic.repository.LanguageRepository;
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
 * Language master workflows ({@code specs/11-P2-Academic-V1.5.md §27.6}).
 *
 * <p>It knows nothing about questions, assessments, or attempts; those consume Language by id.
 */
@Service
public class LanguageService {

    private final LanguageRepository languageRepository;
    private final AcademicMapper mapper;
    private final AcademicValidator validator;
    private final SecurityEventLogger audit;

    public LanguageService(LanguageRepository languageRepository, AcademicMapper mapper, AcademicValidator validator,
            SecurityEventLogger audit) {
        this.languageRepository = languageRepository;
        this.mapper = mapper;
        this.validator = validator;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<AcademicDtos.LanguageListItem> list(Status status, String search, Pageable pageable) {
        Specification<Language> specification = AcademicSpecifications.<Language>status(status)
                .and(AcademicSpecifications.search(search, "code", "name"));
        return languageRepository.findAll(specification, pageable).map(mapper::toListItem);
    }

    @Transactional(readOnly = true)
    public AcademicDtos.LanguageResponse get(UUID id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public AcademicDtos.LanguageResponse create(AcademicDtos.LanguageCreateRequest request, UUID actorId) {
        String code = validator.normalizeCode(request.code(), 20);
        String name = validator.normalizeName(request.name(), 100);
        if (languageRepository.existsByCode(code)) {
            throw new ConflictException("A language with this code already exists");
        }
        Language language = new Language();
        language.setCode(code);
        language.setName(name);
        language.setStatus(Status.ACTIVE);
        Language saved = save(language);
        audit.audit(actorId, "CREATE", "Language", saved.getId(), "Created language " + code);
        return mapper.toResponse(saved);
    }

    @Transactional
    public AcademicDtos.LanguageResponse update(UUID id, AcademicDtos.LanguageUpdateRequest request, UUID actorId) {
        Language language = require(id);
        if (request.name() != null) {
            language.setName(validator.normalizeName(request.name(), 100));
        }
        Status status = validator.validateLifecycleStatus(request.status());
        if (status != null) {
            language.setStatus(status);
        }
        Language saved = save(language);
        audit.audit(actorId, "UPDATE", "Language", saved.getId(), "Updated language " + saved.getCode());
        return mapper.toResponse(saved);
    }

    @Transactional
    public void archive(UUID id, UUID actorId) {
        Language language = require(id);
        language.setStatus(Status.ARCHIVED);
        save(language);
        audit.audit(actorId, "ARCHIVE", "Language", id, "Archived language " + language.getCode());
    }

    private Language require(UUID id) {
        return languageRepository.findById(id).orElseThrow(() -> NotFoundException.of("Language", id));
    }

    private Language save(Language language) {
        try {
            return languageRepository.saveAndFlush(language);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A language with this code already exists");
        }
    }
}
