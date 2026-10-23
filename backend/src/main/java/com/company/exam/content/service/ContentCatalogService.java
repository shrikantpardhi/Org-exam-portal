package com.company.exam.content.service;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.content.api.ContentCatalog;
import com.company.exam.content.entity.Difficulty;
import com.company.exam.content.entity.Question;
import com.company.exam.content.entity.QuestionOption;
import com.company.exam.content.entity.QuestionTag;
import com.company.exam.content.entity.QuestionTemplate;
import com.company.exam.content.entity.QuestionVersion;
import com.company.exam.content.repository.QuestionRepository;
import com.company.exam.content.repository.QuestionTagRepository;
import com.company.exam.content.repository.QuestionTemplateRepository;
import com.company.exam.content.repository.QuestionVersionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final com.company.exam.content.template.QuestionTemplateConfigurationParser
            templateConfigurationParser;

    public ContentCatalogService(QuestionVersionRepository versionRepository, QuestionRepository questionRepository,
            QuestionTemplateRepository templateRepository, QuestionTagRepository questionTagRepository,
            com.company.exam.content.repository.QuestionOptionRepository questionOptionRepository,
            com.company.exam.content.rendering.MarkdownLatexRenderer renderer,
            com.company.exam.content.mapper.ContentMapper mapper,
            com.company.exam.content.template.QuestionTemplateConfigurationParser
                    templateConfigurationParser) {
        this.versionRepository = versionRepository;
        this.questionRepository = questionRepository;
        this.templateRepository = templateRepository;
        this.questionTagRepository = questionTagRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.renderer = renderer;
        this.mapper = mapper;
        this.templateConfigurationParser = templateConfigurationParser;
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
    public Map<UUID, RuntimeQuestion> runtimeQuestions(List<UUID> questionVersionIds) {
        if (questionVersionIds == null || questionVersionIds.isEmpty()) {
            return Map.of();
        }
        // Deduplicate while preserving the caller's order, so repeated version ids do not widen the
        // IN lists and the returned map is built in a stable order.
        List<UUID> requested = new ArrayList<>(new LinkedHashSet<>(questionVersionIds));

        // One read returns each version plus its template's configuration JSON, so the
        // requiresLatexParsing flag costs no extra query (GAP-156).
        List<Object[]> rows = versionRepository.findWithTemplateConfiguration(requested);
        if (rows.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Boolean> requiresLatexByVersionId = new HashMap<>();
        List<QuestionVersion> versions = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            QuestionVersion version = (QuestionVersion) row[0];
            versions.add(version);
            requiresLatexByVersionId.put(version.getId(), templateRequiresLatex((UUID) row[1], (String) row[2]));
        }

        // One read for every option of every version, grouped afterwards. Options are ordered by
        // display_order in SQL and grouped into a LinkedHashMap, so each version's option list keeps
        // that order without a per-version sort.
        Map<UUID, List<RuntimeOption>> optionsByVersionId = new HashMap<>();
        for (QuestionOption option : questionOptionRepository
                .findByQuestionVersionIdInOrderByDisplayOrderAsc(
                        versions.stream().map(QuestionVersion::getId).toList())) {
            optionsByVersionId
                    .computeIfAbsent(option.getQuestionVersionId(), key -> new ArrayList<>())
                    .add(new RuntimeOption(option.getId(), option.getOptionKey(), option.getDisplayOrder(),
                            option.getContent(), renderer.render(option.getContent()).html()));
        }

        Map<UUID, RuntimeQuestion> result = new LinkedHashMap<>();
        for (QuestionVersion version : versions) {
            var rendered = mapper.toResponse(version);
            result.put(version.getId(), new RuntimeQuestion(version.getId(), rendered.contentFormat(),
                    requiresLatexByVersionId.getOrDefault(version.getId(), true),
                    rendered.content(), rendered.renderedContent(),
                    optionsByVersionId.getOrDefault(version.getId(), List.of())));
        }
        return result;
    }

    /**
     * Reads {@code requires_latex_parsing} from a template configuration JSON blob.
     *
     * <p>Delegates to {@link QuestionTemplateConfigurationParser} so the batch read and the
     * single-version read interpret the flag identically -- including the malformed-JSON fallback.
     * A null configuration (no question or no template) yields {@code true}, matching the
     * single-version read's safe default.
     */
    /**
     * Reproduces the single-version read's {@code requiresLatexParsing} decision from a joined row.
     *
     * <p>The single-version read is {@code question.flatMap(template).map(parse).orElse(true)}: a
     * missing question or template yields {@code true} (the safe default), while a present template
     * whose configuration is blank yields {@code false} from the parser. Selecting the template id
     * alongside its configuration is what lets this method tell those two cases apart -- treating a
     * null configuration as "template missing" would silently flip the flag for a template that
     * exists but carries no JSON.
     */
    private boolean templateRequiresLatex(UUID templateId, String templateConfiguration) {
        if (templateId == null) {
            return true;
        }
        return templateConfigurationParser.parseConfiguration(templateConfiguration).requiresLatexParsing();
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
