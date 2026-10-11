package com.company.exam.content.service;

import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.entity.QuestionOption;
import com.company.exam.content.entity.QuestionVersion;
import com.company.exam.content.mapper.ContentMapper;
import com.company.exam.content.rendering.AssetReferenceResolver;
import com.company.exam.content.rendering.ContentValidator;
import com.company.exam.content.repository.QuestionOptionRepository;
import com.company.exam.content.repository.QuestionVersionRepository;
import com.company.exam.content.validation.ContentTextNormalizer;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Question option workflows ({@code specs/12-P3-Content-V1.5.md §38, §76, §29}).
 *
 * <p>Option content uses the same canonical contract. Published-version immutability applies.
 */
@Service
public class QuestionOptionService {

    private final QuestionOptionRepository optionRepository;
    private final QuestionVersionRepository versionRepository;
    private final ContentGuards guards;
    private final ContentValidator contentValidator;
    private final AssetReferenceResolver assetResolver;
    private final ContentTextNormalizer normalizer;
    private final ContentMapper mapper;
    private final SecurityEventLogger audit;

    public QuestionOptionService(QuestionOptionRepository optionRepository, QuestionVersionRepository versionRepository,
            ContentGuards guards, ContentValidator contentValidator, AssetReferenceResolver assetResolver,
            ContentTextNormalizer normalizer, ContentMapper mapper, SecurityEventLogger audit) {
        this.optionRepository = optionRepository;
        this.versionRepository = versionRepository;
        this.guards = guards;
        this.contentValidator = contentValidator;
        this.assetResolver = assetResolver;
        this.normalizer = normalizer;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ContentDtos.OptionResponse> list(UUID questionId, UUID versionId) {
        requireVersion(questionId, versionId);
        return optionRepository.findByQuestionVersionIdOrderByDisplayOrderAsc(versionId).stream()
                .map(mapper::toResponse).toList();
    }

    @Transactional
    public ContentDtos.OptionResponse create(UUID questionId, UUID versionId, ContentDtos.OptionCreateRequest request,
            UUID actorId) {
        QuestionVersion version = requireMutableVersion(questionId, versionId);
        validateOptionContent(version, request.content());
        String optionKey = normalizer.normalizeOptionKey(request.optionKey(), 20);
        if (optionRepository.existsByQuestionVersionIdAndOptionKey(versionId, optionKey)) {
            throw new ConflictException("An option with this key already exists in this version");
        }
        if (optionRepository.existsByQuestionVersionIdAndDisplayOrder(versionId, request.displayOrder())) {
            throw new ConflictException("An option with this display order already exists in this version");
        }
        QuestionOption option = new QuestionOption();
        option.setQuestionVersionId(versionId);
        option.setOptionKey(optionKey);
        option.setContent(normalizer.normalizeOptional(request.content()));
        option.setDisplayOrder(request.displayOrder());
        option.setCorrect(request.isCorrect());
        QuestionOption saved = save(option);
        audit.audit(actorId, "CREATE", "QuestionOption", saved.getId(), "Created option " + optionKey);
        return mapper.toResponse(saved);
    }

    @Transactional
    public ContentDtos.OptionResponse update(UUID questionId, UUID versionId, UUID optionId,
            ContentDtos.OptionUpdateRequest request, UUID actorId) {
        QuestionVersion version = requireMutableVersion(questionId, versionId);
        QuestionOption option = optionRepository.findByIdAndQuestionVersionId(optionId, versionId)
                .orElseThrow(() -> NotFoundException.of("Question option", optionId));
        if (request.content() != null) {
            validateOptionContent(version, request.content());
            option.setContent(normalizer.normalizeOptional(request.content()));
        }
        if (request.displayOrder() != null && !request.displayOrder().equals(option.getDisplayOrder())) {
            if (optionRepository.existsByQuestionVersionIdAndDisplayOrder(versionId, request.displayOrder())) {
                throw new ConflictException("An option with this display order already exists in this version");
            }
            option.setDisplayOrder(request.displayOrder());
        }
        if (request.isCorrect() != null) {
            option.setCorrect(request.isCorrect());
        }
        QuestionOption saved = save(option);
        audit.audit(actorId, "UPDATE", "QuestionOption", saved.getId(), "Updated option " + saved.getOptionKey());
        return mapper.toResponse(saved);
    }

    @Transactional
    public void delete(UUID questionId, UUID versionId, UUID optionId, UUID actorId) {
        requireMutableVersion(questionId, versionId);
        QuestionOption option = optionRepository.findByIdAndQuestionVersionId(optionId, versionId)
                .orElseThrow(() -> NotFoundException.of("Question option", optionId));
        // Draft editing removes the option physically so its key/order can be reused; published
        // versions are immutable and never reach here.
        optionRepository.delete(option);
        audit.audit(actorId, "DELETE", "QuestionOption", optionId, "Deleted draft option " + option.getOptionKey());
    }

    private QuestionVersion requireVersion(UUID questionId, UUID versionId) {
        return versionRepository.findByIdAndQuestionId(versionId, questionId)
                .orElseThrow(() -> NotFoundException.of("Question version", versionId));
    }

    private QuestionVersion requireMutableVersion(UUID questionId, UUID versionId) {
        QuestionVersion version = requireVersion(questionId, versionId);
        guards.requireMutable(version);
        return version;
    }

    private void validateOptionContent(QuestionVersion version, String content) {
        var assetIds = contentValidator.validate(content, "content");
        assetResolver.verifyOwnership(version.getId(), assetIds);
    }

    private QuestionOption save(QuestionOption option) {
        try {
            return optionRepository.saveAndFlush(option);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("An option with this key or display order already exists in this version");
        }
    }
}
