package com.company.exam.academic.service;

import com.company.exam.academic.dto.AcademicDtos;
import com.company.exam.academic.entity.Exam;
import com.company.exam.academic.mapper.AcademicMapper;
import com.company.exam.academic.repository.ExamRepository;
import com.company.exam.academic.repository.SubjectRepository;
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

/** Exam workflows ({@code specs/11-P2-Academic-V1.5.md §27.1}). */
@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final SubjectRepository subjectRepository;
    private final AcademicMapper mapper;
    private final AcademicValidator validator;
    private final SecurityEventLogger audit;

    public ExamService(ExamRepository examRepository, SubjectRepository subjectRepository, AcademicMapper mapper,
            AcademicValidator validator, SecurityEventLogger audit) {
        this.examRepository = examRepository;
        this.subjectRepository = subjectRepository;
        this.mapper = mapper;
        this.validator = validator;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<AcademicDtos.ExamListItem> list(Status status, String search, Pageable pageable) {
        Specification<Exam> specification = AcademicSpecifications.<Exam>status(status)
                .and(AcademicSpecifications.search(search, "code", "name"));
        return examRepository.findAll(specification, pageable).map(mapper::toListItem);
    }

    @Transactional(readOnly = true)
    public AcademicDtos.ExamResponse get(UUID id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public AcademicDtos.ExamResponse create(AcademicDtos.ExamCreateRequest request, UUID actorId) {
        String code = validator.normalizeCode(request.code(), 50);
        String name = validator.normalizeName(request.name(), 150);
        if (examRepository.existsByCode(code)) {
            throw new ConflictException("An exam with this code already exists");
        }
        Exam exam = new Exam();
        exam.setCode(code);
        exam.setName(name);
        exam.setDescription(validator.normalizeDescription(request.description()));
        exam.setStatus(Status.ACTIVE);
        Exam saved = save(exam);
        audit.audit(actorId, "CREATE", "Exam", saved.getId(), "Created exam " + code);
        return mapper.toResponse(saved);
    }

    @Transactional
    public AcademicDtos.ExamResponse update(UUID id, AcademicDtos.ExamUpdateRequest request, UUID actorId) {
        Exam exam = require(id);
        if (request.name() != null) {
            exam.setName(validator.normalizeName(request.name(), 150));
        }
        if (request.description() != null) {
            exam.setDescription(validator.normalizeDescription(request.description()));
        }
        Status status = validator.validateLifecycleStatus(request.status());
        if (status != null) {
            exam.setStatus(status);
        }
        Exam saved = save(exam);
        audit.audit(actorId, "UPDATE", "Exam", saved.getId(), "Updated exam " + saved.getCode());
        return mapper.toResponse(saved);
    }

    @Transactional
    public void archive(UUID id, UUID actorId) {
        Exam exam = require(id);
        validator.requireNoActiveDescendants(
                subjectRepository.countByExamIdAndStatusNot(id, Status.ARCHIVED), "exam");
        exam.setStatus(Status.ARCHIVED);
        save(exam);
        audit.audit(actorId, "ARCHIVE", "Exam", id, "Archived exam " + exam.getCode());
    }

    private Exam require(UUID id) {
        return examRepository.findById(id).orElseThrow(() -> NotFoundException.of("Exam", id));
    }

    private Exam save(Exam exam) {
        try {
            return examRepository.saveAndFlush(exam);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("An exam with this code already exists");
        }
    }
}
