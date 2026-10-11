package com.company.exam.content.service;

import com.company.exam.academic.api.AcademicCatalog;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.entity.PreviousExamHistory;
import com.company.exam.content.mapper.ContentMapper;
import com.company.exam.content.repository.PreviousExamHistoryRepository;
import com.company.exam.content.repository.QuestionRepository;
import com.company.exam.content.validation.ContentTextNormalizer;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Previous-exam-history workflows ({@code specs/12-P3-Content-V1.5.md §44-§48, §79}).
 *
 * <p>Multiple occurrences per question are supported; the referenced exam is verified through the
 * Academic contract. No year range is hard-coded (none is frozen).
 */
@Service
public class PreviousExamHistoryService {

    private final PreviousExamHistoryRepository historyRepository;
    private final QuestionRepository questionRepository;
    private final AcademicCatalog academicCatalog;
    private final ContentTextNormalizer normalizer;
    private final ContentMapper mapper;
    private final SecurityEventLogger audit;

    public PreviousExamHistoryService(PreviousExamHistoryRepository historyRepository,
            QuestionRepository questionRepository, AcademicCatalog academicCatalog, ContentTextNormalizer normalizer,
            ContentMapper mapper, SecurityEventLogger audit) {
        this.historyRepository = historyRepository;
        this.questionRepository = questionRepository;
        this.academicCatalog = academicCatalog;
        this.normalizer = normalizer;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ContentDtos.PreviousExamResponse> list(UUID questionId) {
        requireQuestion(questionId);
        return historyRepository.findByQuestionIdOrderByYearDesc(questionId).stream()
                .filter(history -> history.getStatus() == Status.ACTIVE)
                .map(mapper::toResponse).toList();
    }

    @Transactional
    public ContentDtos.PreviousExamResponse create(UUID questionId, ContentDtos.PreviousExamCreateRequest request,
            UUID actorId) {
        requireQuestion(questionId);
        academicCatalog.requireExam(request.examId());
        PreviousExamHistory history = new PreviousExamHistory();
        history.setQuestionId(questionId);
        history.setExamId(request.examId());
        history.setYear(request.year());
        history.setShift(normalizer.normalizeOptional(request.shift()));
        history.setSource(normalizer.normalizeOptional(request.source()));
        history.setStatus(Status.ACTIVE);
        PreviousExamHistory saved = historyRepository.save(history);
        audit.audit(actorId, "CREATE", "PreviousExamHistory", saved.getId(), "Added previous exam occurrence");
        return mapper.toResponse(saved);
    }

    @Transactional
    public ContentDtos.PreviousExamResponse update(UUID questionId, UUID historyId,
            ContentDtos.PreviousExamUpdateRequest request, UUID actorId) {
        requireQuestion(questionId);
        PreviousExamHistory history = historyRepository.findByIdAndQuestionId(historyId, questionId)
                .orElseThrow(() -> NotFoundException.of("Previous exam history", historyId));
        if (request.year() != null) {
            history.setYear(request.year());
        }
        if (request.shift() != null) {
            history.setShift(normalizer.normalizeOptional(request.shift()));
        }
        if (request.source() != null) {
            history.setSource(normalizer.normalizeOptional(request.source()));
        }
        PreviousExamHistory saved = historyRepository.save(history);
        audit.audit(actorId, "UPDATE", "PreviousExamHistory", saved.getId(), "Updated previous exam occurrence");
        return mapper.toResponse(saved);
    }

    @Transactional
    public void delete(UUID questionId, UUID historyId, UUID actorId) {
        requireQuestion(questionId);
        PreviousExamHistory history = historyRepository.findByIdAndQuestionId(historyId, questionId)
                .orElseThrow(() -> NotFoundException.of("Previous exam history", historyId));
        // Archive rather than physically delete so history is preserved.
        history.setStatus(Status.ARCHIVED);
        historyRepository.save(history);
        audit.audit(actorId, "ARCHIVE", "PreviousExamHistory", historyId, "Archived previous exam occurrence");
    }

    private void requireQuestion(UUID questionId) {
        if (!questionRepository.existsById(questionId)) {
            throw NotFoundException.of("Question", questionId);
        }
    }
}
