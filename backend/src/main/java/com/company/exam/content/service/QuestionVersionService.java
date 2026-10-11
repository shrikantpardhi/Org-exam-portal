package com.company.exam.content.service;

import com.company.exam.academic.api.AcademicCatalog;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.AuthorizationException;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.entity.Question;
import com.company.exam.content.entity.QuestionTemplate;
import com.company.exam.content.entity.QuestionVersion;
import com.company.exam.content.entity.ReviewStatus;
import com.company.exam.content.mapper.ContentMapper;
import com.company.exam.content.rendering.AssetReferenceResolver;
import com.company.exam.content.rendering.ContentValidator;
import com.company.exam.content.repository.QuestionRepository;
import com.company.exam.content.repository.QuestionTemplateRepository;
import com.company.exam.content.repository.QuestionVersionRepository;
import com.company.exam.content.template.QuestionTypeRegistry;
import com.company.exam.content.validation.ContentTextNormalizer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Question version workflows and the review state machine
 * ({@code specs/12-P3-Content-V1.5.md §74, §89-§91, §98}).
 *
 * <pre>
 * DRAFT --submit-review--&gt; IN_REVIEW --approve--&gt; APPROVED --publish--&gt; PUBLISHED
 *                              |
 *                              +--reject--&gt; REJECTED --edit/resubmit--&gt; IN_REVIEW
 * </pre>
 */
@Service
public class QuestionVersionService {

    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository versionRepository;
    private final QuestionTemplateRepository templateRepository;
    private final ContentGuards guards;
    private final ContentValidator contentValidator;
    private final AssetReferenceResolver assetResolver;
    private final QuestionTypeRegistry typeRegistry;
    private final AcademicCatalog academicCatalog;
    private final ContentTextNormalizer normalizer;
    private final ContentMapper mapper;
    private final SecurityEventLogger audit;

    public QuestionVersionService(QuestionRepository questionRepository, QuestionVersionRepository versionRepository,
            QuestionTemplateRepository templateRepository, ContentGuards guards, ContentValidator contentValidator,
            AssetReferenceResolver assetResolver, QuestionTypeRegistry typeRegistry, AcademicCatalog academicCatalog,
            ContentTextNormalizer normalizer, ContentMapper mapper, SecurityEventLogger audit) {
        this.questionRepository = questionRepository;
        this.versionRepository = versionRepository;
        this.templateRepository = templateRepository;
        this.guards = guards;
        this.contentValidator = contentValidator;
        this.assetResolver = assetResolver;
        this.typeRegistry = typeRegistry;
        this.academicCatalog = academicCatalog;
        this.normalizer = normalizer;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional
    public ContentDtos.VersionResponse create(UUID questionId, ContentDtos.VersionCreateRequest request, UUID actorId) {
        Question question = requireQuestion(questionId);
        if (question.getStatus() == com.company.exam.common.enums.Status.ARCHIVED) {
            throw new InvalidStateException("Cannot add a version to an archived question");
        }
        academicCatalog.requireLanguage(request.languageId());
        contentValidator.validate(request.content(), "content");

        int nextVersion = versionRepository.findMaxVersion(questionId) + 1;
        QuestionVersion version = new QuestionVersion();
        version.setQuestionId(questionId);
        version.setVersion(nextVersion);
        version.setTitle(normalizer.normalizeOptional(request.title()));
        version.setContent(request.content());
        version.setMarks(request.marks());
        version.setNegativeMarks(request.negativeMarks());
        version.setDifficulty(request.difficulty());
        version.setLanguageId(request.languageId());
        version.setPublished(false);
        version.setReviewStatus(ReviewStatus.DRAFT);
        QuestionVersion saved = save(version);
        audit.audit(actorId, "CREATE", "QuestionVersion", saved.getId(),
                "Created version " + saved.getVersion() + " of question " + questionId);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ContentDtos.VersionListItem> list(UUID questionId) {
        requireQuestion(questionId);
        return versionRepository.findByQuestionIdOrderByVersionAsc(questionId).stream()
                .map(mapper::toListItem).toList();
    }

    @Transactional(readOnly = true)
    public ContentDtos.VersionResponse get(UUID questionId, UUID versionId) {
        return mapper.toResponse(requireVersion(questionId, versionId));
    }

    @Transactional
    public ContentDtos.VersionResponse submitForReview(UUID questionId, UUID versionId, UUID actorId) {
        QuestionVersion version = requireVersion(questionId, versionId);
        if (version.isPublished()) {
            throw new InvalidStateException("A published version cannot be submitted for review");
        }
        if (version.getReviewStatus() != ReviewStatus.DRAFT && version.getReviewStatus() != ReviewStatus.REJECTED) {
            throw new InvalidStateException("Only a DRAFT or REJECTED version can be submitted for review");
        }
        validateReadyForReview(version);
        version.setReviewStatus(ReviewStatus.IN_REVIEW);
        version.setSubmittedForReviewBy(actorId);
        version.setSubmittedForReviewOn(Instant.now());
        version.setReviewedBy(null);
        version.setReviewedOn(null);
        version.setReviewRemarks(null);
        QuestionVersion saved = versionRepository.save(version);
        audit.audit(actorId, "SUBMIT_REVIEW", "QuestionVersion", saved.getId(), "Submitted for review");
        return mapper.toResponse(saved);
    }

    @Transactional
    public ContentDtos.VersionResponse approve(UUID questionId, UUID versionId, ContentDtos.ReviewRequest request,
            UUID actorId) {
        return review(questionId, versionId, request, actorId, ReviewStatus.APPROVED);
    }

    @Transactional
    public ContentDtos.VersionResponse reject(UUID questionId, UUID versionId, ContentDtos.ReviewRequest request,
            UUID actorId) {
        return review(questionId, versionId, request, actorId, ReviewStatus.REJECTED);
    }

    private ContentDtos.VersionResponse review(UUID questionId, UUID versionId, ContentDtos.ReviewRequest request,
            UUID actorId, ReviewStatus target) {
        QuestionVersion version = requireVersion(questionId, versionId);
        if (version.getReviewStatus() != ReviewStatus.IN_REVIEW) {
            throw new InvalidStateException("Only a version IN_REVIEW can be approved or rejected");
        }
        if (actorId != null && actorId.equals(version.getSubmittedForReviewBy())) {
            throw new AuthorizationException("A user cannot review a version they submitted");
        }
        version.setReviewStatus(target);
        version.setReviewedBy(actorId);
        version.setReviewedOn(Instant.now());
        version.setReviewRemarks(request == null ? null : normalizer.normalizeOptional(request.remarks()));
        QuestionVersion saved = versionRepository.save(version);
        audit.audit(actorId, target.name(), "QuestionVersion", saved.getId(),
                "Review transition to " + target);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public QuestionVersion requireVersion(UUID questionId, UUID versionId) {
        requireQuestion(questionId);
        return versionRepository.findByIdAndQuestionId(versionId, questionId)
                .orElseThrow(() -> NotFoundException.of("Question version", versionId));
    }

    private void validateReadyForReview(QuestionVersion version) {
        if (version.getContent() == null || version.getContent().isBlank()) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("content", "CONTENT_REQUIRED", "Question content is required for review")));
        }
        Question question = requireQuestion(version.getQuestionId());
        QuestionTemplate template = templateRepository.findById(question.getTemplateId())
                .orElseThrow(() -> NotFoundException.of("Question template", question.getTemplateId()));
        typeRegistry.require(template.getCode());
        contentValidator.validate(version.getContent(), "content");
        assetResolver.verifyOwnership(version.getId(), contentValidator.validate(version.getContent(), "content"));
    }

    private Question requireQuestion(UUID id) {
        return questionRepository.findById(id).orElseThrow(() -> NotFoundException.of("Question", id));
    }

    private QuestionVersion save(QuestionVersion version) {
        try {
            return versionRepository.saveAndFlush(version);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A version with this number already exists for the question");
        }
    }
}
