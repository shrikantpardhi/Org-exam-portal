package com.company.exam.content.service;

import com.company.exam.academic.api.AcademicCatalog;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.entity.Question;
import com.company.exam.content.entity.QuestionOption;
import com.company.exam.content.entity.QuestionTemplate;
import com.company.exam.content.entity.QuestionVersion;
import com.company.exam.content.entity.ReviewStatus;
import com.company.exam.content.mapper.ContentMapper;
import com.company.exam.content.rendering.AssetReferenceResolver;
import com.company.exam.content.rendering.ContentValidator;
import com.company.exam.content.repository.ExplanationRepository;
import com.company.exam.content.repository.HintRepository;
import com.company.exam.content.repository.QuestionOptionRepository;
import com.company.exam.content.repository.QuestionRepository;
import com.company.exam.content.repository.QuestionTemplateRepository;
import com.company.exam.content.repository.QuestionVersionRepository;
import com.company.exam.content.template.QuestionTypeRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The publication correctness boundary ({@code specs/12-P3-Content-V1.5.md §64-§67, §75, §99}).
 *
 * <p>Publication is a controlled validation and transition, not a generic flag update. It fails if
 * any precondition cannot be satisfied; on success the version becomes immutable.
 */
@Service
public class QuestionPublicationService {

    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository versionRepository;
    private final QuestionOptionRepository optionRepository;
    private final QuestionTemplateRepository templateRepository;
    private final ExplanationRepository explanationRepository;
    private final HintRepository hintRepository;
    private final QuestionTypeRegistry typeRegistry;
    private final ContentValidator contentValidator;
    private final AssetReferenceResolver assetResolver;
    private final AcademicCatalog academicCatalog;
    private final ContentMapper mapper;
    private final SecurityEventLogger audit;

    public QuestionPublicationService(QuestionRepository questionRepository,
            QuestionVersionRepository versionRepository, QuestionOptionRepository optionRepository,
            QuestionTemplateRepository templateRepository, ExplanationRepository explanationRepository,
            HintRepository hintRepository, QuestionTypeRegistry typeRegistry, ContentValidator contentValidator,
            AssetReferenceResolver assetResolver, AcademicCatalog academicCatalog, ContentMapper mapper,
            SecurityEventLogger audit) {
        this.questionRepository = questionRepository;
        this.versionRepository = versionRepository;
        this.optionRepository = optionRepository;
        this.templateRepository = templateRepository;
        this.explanationRepository = explanationRepository;
        this.hintRepository = hintRepository;
        this.typeRegistry = typeRegistry;
        this.contentValidator = contentValidator;
        this.assetResolver = assetResolver;
        this.academicCatalog = academicCatalog;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional
    public ContentDtos.VersionResponse publish(UUID questionId, UUID versionId, UUID actorId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> NotFoundException.of("Question", questionId));
        QuestionVersion version = versionRepository.findByIdAndQuestionId(versionId, questionId)
                .orElseThrow(() -> NotFoundException.of("Question version", versionId));

        if (version.isPublished()) {
            throw new ConflictException("This question version is already published");
        }
        if (version.getReviewStatus() != ReviewStatus.APPROVED) {
            throw new InvalidStateException("Only an APPROVED version can be published");
        }

        QuestionTemplate template = templateRepository.findById(question.getTemplateId())
                .orElseThrow(() -> NotFoundException.of("Question template", question.getTemplateId()));
        String templateCode = template.getCode();
        typeRegistry.require(templateCode);

        // Content.
        if (version.getContent() == null || version.getContent().isBlank()) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("content", "CONTENT_REQUIRED", "Question content is required")));
        }
        List<UUID> assetIds = new ArrayList<>(contentValidator.validate(version.getContent(), "content"));

        // Options and correctness.
        List<QuestionOption> options = optionRepository.findByQuestionVersionIdOrderByDisplayOrderAsc(versionId);
        long correctOptions = options.stream().filter(QuestionOption::isCorrect).count();
        for (QuestionOption option : options) {
            assetIds.addAll(contentValidator.validate(option.getContent(), "options"));
        }
        typeRegistry.validateOptions(templateCode, options.size(), (int) correctOptions);

        // Taxonomy and language.
        if (question.getExamId() == null || question.getSubjectId() == null) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("taxonomy", "TAXONOMY_REQUIRED",
                            "examId and subjectId are required for publication")));
        }
        if (version.getLanguageId() == null) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("languageId", "LANGUAGE_REQUIRED", "languageId is required for publication")));
        }
        academicCatalog.requireExam(question.getExamId());
        academicCatalog.requireSubject(question.getSubjectId());
        academicCatalog.requireLanguage(version.getLanguageId());

        // Explanation and hint content, when present.
        explanationRepository.findFirstByQuestionVersionIdOrderByCreatedOnAsc(versionId)
                .ifPresent(explanation -> assetIds.addAll(contentValidator.validate(explanation.getContent(), "explanation")));
        hintRepository.findFirstByQuestionVersionIdOrderByCreatedOnAsc(versionId)
                .ifPresent(hint -> assetIds.addAll(contentValidator.validate(hint.getContent(), "hint")));

        // Assets referenced by the version must exist and belong to it.
        assetResolver.verifyOwnership(versionId, assetIds);

        version.setPublished(true);
        version.setPublishedBy(actorId);
        version.setPublishedOn(Instant.now());
        version.setReviewStatus(ReviewStatus.PUBLISHED);
        QuestionVersion saved = versionRepository.save(version);
        audit.audit(actorId, "PUBLISH", "QuestionVersion", saved.getId(), "Published question version");
        return mapper.toResponse(saved);
    }
}
