package com.company.exam.content.service;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.content.api.ContentCatalog;
import com.company.exam.content.entity.Difficulty;
import com.company.exam.content.entity.Question;
import com.company.exam.content.entity.QuestionTag;
import com.company.exam.content.entity.QuestionTemplate;
import com.company.exam.content.entity.QuestionVersion;
import com.company.exam.content.repository.QuestionRepository;
import com.company.exam.content.repository.QuestionTagRepository;
import com.company.exam.content.repository.QuestionTemplateRepository;
import com.company.exam.content.repository.QuestionVersionRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link ContentCatalog} ({@code specs/13-P4-Assessment-V1.5.md §82}). */
@Service
public class ContentCatalogService implements ContentCatalog {

    private final QuestionVersionRepository versionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionTemplateRepository templateRepository;
    private final QuestionTagRepository questionTagRepository;
    private final com.company.exam.content.repository.QuestionOptionRepository questionOptionRepository;
    private final com.company.exam.content.rendering.MarkdownLatexRenderer renderer;
    private final com.company.exam.content.mapper.ContentMapper mapper;

    public ContentCatalogService(QuestionVersionRepository versionRepository, QuestionRepository questionRepository,
            QuestionTemplateRepository templateRepository, QuestionTagRepository questionTagRepository,
            com.company.exam.content.repository.QuestionOptionRepository questionOptionRepository,
            com.company.exam.content.rendering.MarkdownLatexRenderer renderer,
            com.company.exam.content.mapper.ContentMapper mapper) {
        this.versionRepository = versionRepository;
        this.questionRepository = questionRepository;
        this.templateRepository = templateRepository;
        this.questionTagRepository = questionTagRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.renderer = renderer;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public RuntimeQuestion runtimeQuestion(UUID questionVersionId) {
        QuestionVersion version = versionRepository.findById(questionVersionId)
                .orElseThrow(() -> NotFoundException.of("Question version", questionVersionId));
        var rendered = mapper.toResponse(version);
        boolean requiresLatex = questionRepository.findById(version.getQuestionId())
                .flatMap(question -> templateRepository.findById(question.getTemplateId()))
                .map(template -> mapper.toResponse(template).requiresLatexParsing())
                .orElse(true);
        List<RuntimeOption> options = questionOptionRepository
                .findByQuestionVersionIdOrderByDisplayOrderAsc(questionVersionId).stream()
                .map(option -> new RuntimeOption(option.getId(), option.getOptionKey(), option.getDisplayOrder(),
                        option.getContent(), renderer.render(option.getContent()).html()))
                .toList();
        return new RuntimeQuestion(version.getId(), rendered.contentFormat(), requiresLatex, rendered.content(),
                rendered.renderedContent(), options);
    }


    @Override
    @Transactional(readOnly = true)
    public PreviewContent previewContent(UUID questionVersionId) {
        QuestionVersion version = versionRepository.findById(questionVersionId)
                .orElseThrow(() -> NotFoundException.of("Question version", questionVersionId));
        var rendered = mapper.toResponse(version);
        return new PreviewContent(rendered.contentFormat(), rendered.content(), rendered.renderedContent());
    }

    @Override
    @Transactional(readOnly = true)
    public PublishedQuestionVersion requirePublishedQuestionVersion(UUID questionVersionId) {
        QuestionVersion version = versionRepository.findById(questionVersionId)
                .orElseThrow(() -> new BusinessException("Question version not found",
                        List.of(ApiError.of("questionVersionId", "QUESTION_VERSION_NOT_FOUND",
                                "Question version does not exist"))));
        if (!version.isPublished()) {
            throw new BusinessException("Question version is not published",
                    List.of(ApiError.of("questionVersionId", "QUESTION_VERSION_NOT_PUBLISHED",
                            "Only published question versions may be used")));
        }
        return toSummary(version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublishedQuestionVersion> findEligible(SelectionCriteria criteria) {
        Set<Difficulty> difficulties = parseDifficulties(criteria.difficulties());
        List<QuestionVersion> candidates = versionRepository.findEligibleVersions(
                criteria.templateCode(),
                criteria.examId(),
                criteria.subjectId(),
                criteria.chapterId(),
                criteria.topicId(),
                criteria.languageId(),
                difficulties.isEmpty(),
                difficulties.isEmpty() ? new ArrayList<>(List.of(Difficulty.EASY)) : new ArrayList<>(difficulties),
                safe(criteria.excludeQuestionIds()),
                safe(criteria.excludeQuestionVersionIds()));

        if (criteria.tagIds() == null || criteria.tagIds().isEmpty()) {
            return candidates.stream().map(this::toSummary).toList();
        }
        Set<UUID> wantedTags = new HashSet<>(criteria.tagIds());
        return candidates.stream()
                .filter(version -> hasAnyTag(version.getQuestionId(), wantedTags))
                .map(this::toSummary)
                .toList();
    }

    private boolean hasAnyTag(UUID questionId, Set<UUID> tagIds) {
        return questionTagRepository.findByQuestionId(questionId).stream()
                .filter(association -> association.getStatus() == com.company.exam.common.enums.Status.ACTIVE)
                .anyMatch(association -> tagIds.contains(association.getTagId()));
    }

    private PublishedQuestionVersion toSummary(QuestionVersion version) {
        Question question = questionRepository.findById(version.getQuestionId()).orElse(null);
        String templateCode = question == null ? null
                : templateRepository.findById(question.getTemplateId()).map(QuestionTemplate::getCode).orElse(null);
        return new PublishedQuestionVersion(version.getId(), version.getQuestionId(), version.getLanguageId(),
                version.getDifficulty() == null ? null : version.getDifficulty().name(),
                version.getMarks(), version.getNegativeMarks(), templateCode, version.isPublished());
    }

    private Set<Difficulty> parseDifficulties(List<String> values) {
        Set<Difficulty> result = new HashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null) {
                    try {
                        result.add(Difficulty.valueOf(value.trim().toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                        // Unknown difficulty is ignored rather than widening the search.
                    }
                }
            }
        }
        return result;
    }

    private List<UUID> safe(List<UUID> values) {
        return values == null || values.isEmpty() ? List.of(PLACEHOLDER) : values;
    }

    private static final UUID PLACEHOLDER = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
}
