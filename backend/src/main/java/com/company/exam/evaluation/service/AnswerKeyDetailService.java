package com.company.exam.evaluation.service;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.evaluation.dto.EvaluationDtos;
import com.company.exam.evaluation.entity.AnswerKey;
import com.company.exam.evaluation.entity.AnswerKeyDetail;
import com.company.exam.evaluation.entity.AnswerKeyDetailOption;
import com.company.exam.evaluation.entity.QuestionOutcome;
import com.company.exam.evaluation.exception.AnswerKeyImmutableException;
import com.company.exam.evaluation.mapper.EvaluationMapper;
import com.company.exam.evaluation.repository.AnswerKeyDetailOptionRepository;
import com.company.exam.evaluation.repository.AnswerKeyDetailRepository;
import com.company.exam.evaluation.validation.AnswerKeyValidator;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answer key detail CRUD ({@code specs/15-P6-Evaluation-V1.5.md §30, §88, §121}).
 *
 * <p>Mutations are blocked once the key is published ({@code §30}); the key is loaded through
 * {@link AnswerKeyService} so a detail can never be attached to an unknown key.
 */
@Service
public class AnswerKeyDetailService {

    private final AnswerKeyDetailRepository detailRepository;
    private final AnswerKeyDetailOptionRepository optionRepository;
    private final AnswerKeyService answerKeyService;
    private final AnswerKeyValidator validator;
    private final EvaluationMapper mapper;
    private final SecurityEventLogger audit;

    public AnswerKeyDetailService(AnswerKeyDetailRepository detailRepository,
            AnswerKeyDetailOptionRepository optionRepository, AnswerKeyService answerKeyService,
            AnswerKeyValidator validator, EvaluationMapper mapper, SecurityEventLogger audit) {
        this.detailRepository = detailRepository;
        this.optionRepository = optionRepository;
        this.answerKeyService = answerKeyService;
        this.validator = validator;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<EvaluationDtos.AnswerKeyDetailResponse> list(UUID answerKeyId) {
        answerKeyService.require(answerKeyId);
        return detailRepository.findByAnswerKeyId(answerKeyId).stream()
                .map(detail -> mapper.toResponse(detail, optionIds(detail.getId())))
                .toList();
    }

    private List<UUID> optionIds(UUID detailId) {
        return optionRepository.findByAnswerKeyDetailId(detailId).stream()
                .map(AnswerKeyDetailOption::getQuestionOptionId).toList();
    }

    @Transactional
    public EvaluationDtos.AnswerKeyDetailResponse create(UUID answerKeyId, EvaluationDtos.AnswerKeyDetailRequest request,
            UUID actorId) {
        AnswerKey key = requireMutable(answerKeyId);
        QuestionOutcome outcome = validator.requireOutcome(request.questionOutcome());
        validator.requireQuestionInAssessmentVersion(key.getAssessmentVersionId(), request.questionVersionId());
        validator.validateMapping(request, outcome);

        AnswerKeyDetail detail = new AnswerKeyDetail();
        detail.setAnswerKeyId(answerKeyId);
        applyRequest(detail, request, outcome);
        try {
            AnswerKeyDetail saved = detailRepository.saveAndFlush(detail);
            replaceOptions(saved.getId(), request.correctOptionIds());
            audit.audit(actorId, "CREATE", "AnswerKeyDetail", saved.getId(), "Created answer key detail");
            return mapper.toResponse(saved, optionIds(saved.getId()));
        } catch (DataIntegrityViolationException duplicate) {
            throw new ConflictException("An answer key detail already exists for that question version");
        }
    }

    @Transactional
    public EvaluationDtos.AnswerKeyDetailResponse update(UUID answerKeyId, UUID detailId,
            EvaluationDtos.AnswerKeyDetailRequest request, UUID actorId) {
        AnswerKey key = requireMutable(answerKeyId);
        AnswerKeyDetail detail = detailRepository.findByIdAndAnswerKeyId(detailId, answerKeyId)
                .orElseThrow(() -> NotFoundException.of("Answer key detail", detailId));
        QuestionOutcome outcome = validator.requireOutcome(request.questionOutcome());
        validator.validateMapping(request, outcome);
        applyRequest(detail, request, outcome);
        AnswerKeyDetail saved = detailRepository.saveAndFlush(detail);
        replaceOptions(saved.getId(), request.correctOptionIds());
        audit.audit(actorId, "UPDATE", "AnswerKeyDetail", saved.getId(), "Updated answer key detail");
        return mapper.toResponse(saved, optionIds(saved.getId()));
    }

    @Transactional
    public void delete(UUID answerKeyId, UUID detailId, UUID actorId) {
        requireMutable(answerKeyId);
        AnswerKeyDetail detail = detailRepository.findByIdAndAnswerKeyId(detailId, answerKeyId)
                .orElseThrow(() -> NotFoundException.of("Answer key detail", detailId));
        optionRepository.deleteByAnswerKeyDetailId(detailId);
        detailRepository.delete(detail);
        audit.audit(actorId, "DELETE", "AnswerKeyDetail", detailId, "Deleted answer key detail");
    }

    private AnswerKey requireMutable(UUID answerKeyId) {
        AnswerKey key = answerKeyService.require(answerKeyId);
        if (key.isPublished()) {
            throw new AnswerKeyImmutableException("A published answer key cannot be modified");
        }
        return key;
    }

    private void applyRequest(AnswerKeyDetail detail, EvaluationDtos.AnswerKeyDetailRequest request,
            QuestionOutcome outcome) {
        detail.setQuestionVersionId(request.questionVersionId());
        detail.setCorrectOptionId(request.correctOptionId());
        detail.setCorrectAnswerText(request.correctAnswerText());
        detail.setQuestionOutcome(outcome);
        detail.setAwardedMarks(request.awardedMarks());
        detail.setAwardedNegativeMarks(request.awardedNegativeMarks());
        detail.setRemarks(request.remarks());
    }

    private void replaceOptions(UUID detailId, List<UUID> optionIds) {
        optionRepository.deleteByAnswerKeyDetailId(detailId);
        if (optionIds == null) {
            return;
        }
        for (UUID optionId : optionIds) {
            AnswerKeyDetailOption row = new AnswerKeyDetailOption();
            row.setAnswerKeyDetailId(detailId);
            row.setQuestionOptionId(optionId);
            optionRepository.save(row);
        }
    }
}
