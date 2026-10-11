package com.company.exam.content.service;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.entity.Hint;
import com.company.exam.content.entity.QuestionVersion;
import com.company.exam.content.mapper.ContentMapper;
import com.company.exam.content.rendering.AssetReferenceResolver;
import com.company.exam.content.rendering.ContentValidator;
import com.company.exam.content.repository.HintRepository;
import com.company.exam.content.repository.QuestionVersionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Version-bound, optional hint workflows ({@code specs/12-P3-Content-V1.5.md §40, §78}).
 *
 * <p>Published-version immutability applies.
 */
@Service
public class HintService {

    private final HintRepository hintRepository;
    private final QuestionVersionRepository versionRepository;
    private final ContentGuards guards;
    private final ContentValidator contentValidator;
    private final AssetReferenceResolver assetResolver;
    private final ContentMapper mapper;
    private final SecurityEventLogger audit;

    public HintService(HintRepository hintRepository, QuestionVersionRepository versionRepository,
            ContentGuards guards, ContentValidator contentValidator, AssetReferenceResolver assetResolver,
            ContentMapper mapper, SecurityEventLogger audit) {
        this.hintRepository = hintRepository;
        this.versionRepository = versionRepository;
        this.guards = guards;
        this.contentValidator = contentValidator;
        this.assetResolver = assetResolver;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ContentDtos.HintResponse get(UUID versionId) {
        requireVersion(versionId);
        return hintRepository.findFirstByQuestionVersionIdOrderByCreatedOnAsc(versionId)
                .map(mapper::toResponse)
                .orElseThrow(() -> NotFoundException.of("Hint", versionId));
    }

    @Transactional
    public ContentDtos.HintResponse create(UUID versionId, ContentDtos.TextContentRequest request, UUID actorId) {
        QuestionVersion version = requireMutableVersion(versionId);
        validate(version, request.content());
        if (hintRepository.findFirstByQuestionVersionIdOrderByCreatedOnAsc(versionId).isPresent()) {
            throw new ConflictException("A hint already exists for this version; use PUT to update it");
        }
        Hint hint = new Hint();
        hint.setQuestionVersionId(versionId);
        hint.setContent(request.content());
        hint.setStatus(Status.ACTIVE);
        Hint saved = hintRepository.save(hint);
        audit.audit(actorId, "CREATE", "Hint", saved.getId(), "Created hint");
        return mapper.toResponse(saved);
    }

    @Transactional
    public ContentDtos.HintResponse update(UUID versionId, ContentDtos.TextContentRequest request, UUID actorId) {
        QuestionVersion version = requireMutableVersion(versionId);
        validate(version, request.content());
        Hint hint = hintRepository.findFirstByQuestionVersionIdOrderByCreatedOnAsc(versionId)
                .orElseThrow(() -> NotFoundException.of("Hint", versionId));
        hint.setContent(request.content());
        Hint saved = hintRepository.save(hint);
        audit.audit(actorId, "UPDATE", "Hint", saved.getId(), "Updated hint");
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
