package com.company.exam.content.service;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.entity.Explanation;
import com.company.exam.content.entity.QuestionVersion;
import com.company.exam.content.mapper.ContentMapper;
import com.company.exam.content.rendering.AssetReferenceResolver;
import com.company.exam.content.rendering.ContentValidator;
import com.company.exam.content.repository.ExplanationRepository;
import com.company.exam.content.repository.QuestionVersionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Version-bound explanation workflows ({@code specs/12-P3-Content-V1.5.md §39, §78}).
 *
 * <p>One explanation per version conceptually; published-version immutability applies.
 */
@Service
public class ExplanationService {

    private final ExplanationRepository explanationRepository;
    private final QuestionVersionRepository versionRepository;
    private final ContentGuards guards;
    private final ContentValidator contentValidator;
    private final AssetReferenceResolver assetResolver;
    private final ContentMapper mapper;
    private final SecurityEventLogger audit;

    public ExplanationService(ExplanationRepository explanationRepository,
            QuestionVersionRepository versionRepository, ContentGuards guards, ContentValidator contentValidator,
            AssetReferenceResolver assetResolver, ContentMapper mapper, SecurityEventLogger audit) {
        this.explanationRepository = explanationRepository;
        this.versionRepository = versionRepository;
        this.guards = guards;
        this.contentValidator = contentValidator;
        this.assetResolver = assetResolver;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ContentDtos.ExplanationResponse get(UUID versionId) {
        requireVersion(versionId);
        return explanationRepository.findFirstByQuestionVersionIdOrderByCreatedOnAsc(versionId)
                .map(mapper::toResponse)
                .orElseThrow(() -> NotFoundException.of("Explanation", versionId));
    }

    @Transactional
    public ContentDtos.ExplanationResponse create(UUID versionId, ContentDtos.TextContentRequest request, UUID actorId) {
        QuestionVersion version = requireMutableVersion(versionId);
        validate(version, request.content());
        if (explanationRepository.findFirstByQuestionVersionIdOrderByCreatedOnAsc(versionId).isPresent()) {
            throw new ConflictException("An explanation already exists for this version; use PUT to update it");
        }
        Explanation explanation = new Explanation();
        explanation.setQuestionVersionId(versionId);
        explanation.setContent(request.content());
        explanation.setStatus(Status.ACTIVE);
        Explanation saved = explanationRepository.save(explanation);
        audit.audit(actorId, "CREATE", "Explanation", saved.getId(), "Created explanation");
        return mapper.toResponse(saved);
    }

    @Transactional
    public ContentDtos.ExplanationResponse update(UUID versionId, ContentDtos.TextContentRequest request, UUID actorId) {
        QuestionVersion version = requireMutableVersion(versionId);
        validate(version, request.content());
        Explanation explanation = explanationRepository.findFirstByQuestionVersionIdOrderByCreatedOnAsc(versionId)
                .orElseThrow(() -> NotFoundException.of("Explanation", versionId));
        explanation.setContent(request.content());
        Explanation saved = explanationRepository.save(explanation);
        audit.audit(actorId, "UPDATE", "Explanation", saved.getId(), "Updated explanation");
        return mapper.toResponse(saved);
    }

    private void validate(QuestionVersion version, String content) {
        assetResolver.verifyOwnership(version.getId(), contentValidator.validate(content, "content"));
    }

    private QuestionVersion requireVersion(UUID versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> NotFoundException.of("Question version", versionId));
    }

    private QuestionVersion requireMutableVersion(UUID versionId) {
        QuestionVersion version = requireVersion(versionId);
        guards.requireMutable(version);
        return version;
    }
}
