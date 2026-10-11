package com.company.exam.academic.service;

import com.company.exam.academic.dto.AcademicDtos;
import com.company.exam.academic.entity.Exam;
import com.company.exam.academic.entity.Subject;
import com.company.exam.academic.mapper.AcademicMapper;
import com.company.exam.academic.repository.ChapterRepository;
import com.company.exam.academic.repository.ExamRepository;
import com.company.exam.academic.repository.SubjectRepository;
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

/** Subject workflows ({@code specs/11-P2-Academic-V1.5.md §27.2}). */
@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final ExamRepository examRepository;
    private final ChapterRepository chapterRepository;
    private final AcademicMapper mapper;
    private final AcademicValidator validator;
    private final SecurityEventLogger audit;

    public SubjectService(SubjectRepository subjectRepository, ExamRepository examRepository,
            ChapterRepository chapterRepository, AcademicMapper mapper, AcademicValidator validator,
            SecurityEventLogger audit) {
        this.subjectRepository = subjectRepository;
        this.examRepository = examRepository;
        this.chapterRepository = chapterRepository;
        this.mapper = mapper;
        this.validator = validator;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<AcademicDtos.SubjectListItem> list(UUID examId, Status status, String search, Pageable pageable) {
        if (examId == null) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("examId", "REQUIRED_FILTER", "examId is required")));
        }
        Specification<Subject> specification = AcademicSpecifications.<Subject>parentEquals("examId", examId)
                .and(AcademicSpecifications.status(status))
                .and(AcademicSpecifications.search(search, "code", "name"));
        return subjectRepository.findAll(specification, pageable).map(mapper::toListItem);
    }

    @Transactional(readOnly = true)
    public AcademicDtos.SubjectResponse get(UUID id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public AcademicDtos.SubjectResponse create(AcademicDtos.SubjectCreateRequest request, UUID actorId) {
        Exam exam = examRepository.findById(request.examId())
                .orElseThrow(() -> NotFoundException.of("Exam", request.examId()));
        validator.requireParentEligible(exam.getStatus(), "exam");
        String code = validator.normalizeCode(request.code(), 50);
        String name = validator.normalizeName(request.name(), 150);
        if (subjectRepository.existsByExamIdAndCode(request.examId(), code)) {
            throw new ConflictException("A subject with this code already exists in this exam");
        }
        Subject subject = new Subject();
        subject.setExamId(request.examId());
        subject.setCode(code);
        subject.setName(name);
        subject.setDescription(validator.normalizeDescription(request.description()));
        subject.setStatus(Status.ACTIVE);
        Subject saved = save(subject);
        audit.audit(actorId, "CREATE", "Subject", saved.getId(), "Created subject " + code);
        return mapper.toResponse(saved);
    }

    @Transactional
    public AcademicDtos.SubjectResponse update(UUID id, AcademicDtos.SubjectUpdateRequest request, UUID actorId) {
        Subject subject = require(id);
        if (request.name() != null) {
            subject.setName(validator.normalizeName(request.name(), 150));
        }
        if (request.description() != null) {
            subject.setDescription(validator.normalizeDescription(request.description()));
        }
        Status status = validator.validateLifecycleStatus(request.status());
        if (status != null) {
            subject.setStatus(status);
        }
        Subject saved = save(subject);
        audit.audit(actorId, "UPDATE", "Subject", saved.getId(), "Updated subject " + saved.getCode());
        return mapper.toResponse(saved);
    }

    @Transactional
    public void archive(UUID id, UUID actorId) {
        Subject subject = require(id);
        validator.requireNoActiveDescendants(
                chapterRepository.countBySubjectIdAndStatusNot(id, Status.ARCHIVED), "subject");
        subject.setStatus(Status.ARCHIVED);
        save(subject);
        audit.audit(actorId, "ARCHIVE", "Subject", id, "Archived subject " + subject.getCode());
    }

    private Subject require(UUID id) {
        return subjectRepository.findById(id).orElseThrow(() -> NotFoundException.of("Subject", id));
    }

    private Subject save(Subject subject) {
        try {
            return subjectRepository.saveAndFlush(subject);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A subject with this code already exists in this exam");
        }
    }
}
