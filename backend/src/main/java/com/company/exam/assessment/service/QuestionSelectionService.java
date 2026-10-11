package com.company.exam.assessment.service;

import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.entity.AssessmentQuestion;
import com.company.exam.assessment.entity.AssessmentSelectionRule;
import com.company.exam.assessment.entity.AssessmentVersion;
import com.company.exam.assessment.mapper.AssessmentMapper;
import com.company.exam.assessment.repository.AssessmentQuestionRepository;
import com.company.exam.assessment.repository.AssessmentSelectionRuleRepository;
import com.company.exam.assessment.repository.AssessmentVersionRepository;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.content.api.ContentCatalog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Rule-based and hybrid question materialization
 * ({@code specs/13-P4-Assessment-V1.5.md §46-§49, §79, §188}).
 *
 * <p>Materialization is deterministic (difficulty EASY -&gt; MEDIUM -&gt; HARD, then question version id
 * ascending) and idempotent (rules already materialized are skipped). Manual, rule-based, and hybrid
 * authoring are all expressed as concrete AssessmentQuestion rows; Delivery never re-runs authoring
 * rules.
 */
@Service
public class QuestionSelectionService {

    private static final String MODE_REPLACE_UNMATERIALIZED = "REPLACE_UNMATERIALIZED";

    private final AssessmentVersionRepository versionRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentSelectionRuleRepository ruleRepository;
    private final ContentCatalog contentCatalog;
    private final AssessmentMapper mapper;
    private final ObjectMapper objectMapper;
    private final SecurityEventLogger audit;

    public QuestionSelectionService(AssessmentVersionRepository versionRepository,
            AssessmentQuestionRepository questionRepository, AssessmentSelectionRuleRepository ruleRepository,
            ContentCatalog contentCatalog, AssessmentMapper mapper, ObjectMapper objectMapper,
            SecurityEventLogger audit) {
        this.versionRepository = versionRepository;
        this.questionRepository = questionRepository;
        this.ruleRepository = ruleRepository;
        this.contentCatalog = contentCatalog;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    /**
     * Materializes every unmaterialized rule of an assessment version.
     *
     * @return the assessment questions that now exist for the version
     */
    @Transactional
    public List<AssessmentDtos.AssessmentQuestionResponse> materialize(UUID versionId, String mode, UUID actorId) {
        if (!MODE_REPLACE_UNMATERIALIZED.equals(mode)) {
            throw new ValidationException("Validation failed",
                    List.of(ApiError.of("mode", "VALIDATION_ERROR",
                            "Only REPLACE_UNMATERIALIZED materialization is supported")));
        }
        AssessmentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> NotFoundException.of("Assessment version", versionId));
        if (version.isPublished()) {
            throw new com.company.exam.common.exception.InvalidStateException(
                    "Published assessment versions are immutable");
        }

        List<AssessmentQuestion> existing = questionRepository.findByAssessmentVersionIdOrderByDisplayOrderAsc(versionId);
        Set<UUID> usedVersionIds = new HashSet<>(existing.stream().map(AssessmentQuestion::getQuestionVersionId).toList());
        int nextOrder = existing.stream().mapToInt(AssessmentQuestion::getDisplayOrder).max().orElse(0) + 1;

        List<AssessmentSelectionRule> rules = ruleRepository.findByAssessmentVersionIdOrderByDisplayOrderAsc(versionId);
        for (AssessmentSelectionRule rule : rules) {
            if (rule.getMaterializedOn() != null) {
                continue; // idempotent: already materialized rules are not re-run
            }
            List<ContentCatalog.PublishedQuestionVersion> eligible = contentCatalog.findEligible(
                    criteria(rule.getRuleConfiguration(), usedVersionIds));
            List<ContentCatalog.PublishedQuestionVersion> ordered = new ArrayList<>(eligible);
            ordered.sort(Comparator
                    .comparingInt((ContentCatalog.PublishedQuestionVersion v) -> difficultyOrdinal(v.difficulty()))
                    .thenComparing(ContentCatalog.PublishedQuestionVersion::questionVersionId));

            int target = rule.getTargetCount();
            if (ordered.size() < target) {
                int available = ordered.size();
                throw new BusinessException("Question pool shortage for section: " + rule.getSectionName(),
                        List.of(ApiError.of("sectionName", "QUESTION_POOL_SHORTAGE",
                                "Question pool shortage for section: " + rule.getSectionName()
                                        + " (requested " + target + ", available " + available
                                        + ", deficit " + (target - available) + ")")));
            }
            for (int index = 0; index < target; index++) {
                ContentCatalog.PublishedQuestionVersion chosen = ordered.get(index);
                AssessmentQuestion question = new AssessmentQuestion();
                question.setAssessmentVersionId(versionId);
                question.setQuestionVersionId(chosen.questionVersionId());
                question.setDisplayOrder(nextOrder++);
                question.setSectionName(rule.getSectionName());
                question.setMarks(chosen.marks());
                question.setNegativeMarks(chosen.negativeMarks());
                question.setStatus(Status.ACTIVE);
                questionRepository.save(question);
                usedVersionIds.add(chosen.questionVersionId());
            }
            rule.setMaterializedOn(Instant.now());
            rule.setMaterializedQuestionCount(target);
            ruleRepository.save(rule);
        }
        audit.audit(actorId, "MATERIALIZE", "AssessmentVersion", versionId, "Materialized selection rules");
        return questionRepository.findByAssessmentVersionIdOrderByDisplayOrderAsc(versionId).stream()
                .map(mapper::toResponse).toList();
    }

    private ContentCatalog.SelectionCriteria criteria(String ruleConfiguration, Set<UUID> usedVersionIds) {
        JsonNode node;
        try {
            node = ruleConfiguration == null ? null : objectMapper.readTree(ruleConfiguration);
        } catch (Exception invalid) {
            node = null;
        }
        List<String> difficulties = new ArrayList<>();
        if (node != null && node.has("difficulty")) {
            node.get("difficulty").forEach(value -> difficulties.add(value.asString()));
        }
        List<UUID> tagIds = uuidList(node, "tagIds");
        List<UUID> excludeQuestionIds = uuidList(node, "excludeQuestionIds");
        List<UUID> excludeQuestionVersionIds = new ArrayList<>(uuidList(node, "excludeQuestionVersionIds"));
        excludeQuestionVersionIds.addAll(usedVersionIds);
        return new ContentCatalog.SelectionCriteria(
                text(node, "templateCode"),
                uuid(node, "examId"),
                uuid(node, "subjectId"),
                uuid(node, "chapterId"),
                uuid(node, "topicId"),
                difficulties,
                tagIds,
                uuid(node, "languageId"),
                node == null || !node.has("publishedOnly") || node.get("publishedOnly").asBoolean(),
                excludeQuestionIds,
                excludeQuestionVersionIds);
    }

    private String text(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asString() : null;
    }

    private UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private List<UUID> uuidList(JsonNode node, String field) {
        List<UUID> values = new ArrayList<>();
        if (node != null && node.has(field)) {
            node.get(field).forEach(value -> {
                try {
                    values.add(UUID.fromString(value.asString()));
                } catch (IllegalArgumentException ignored) {
                    // ignore malformed ids
                }
            });
        }
        return values;
    }

    private int difficultyOrdinal(String difficulty) {
        if (difficulty == null) {
            return 3;
        }
        return switch (difficulty.toUpperCase(java.util.Locale.ROOT)) {
            case "EASY" -> 0;
            case "MEDIUM" -> 1;
            case "HARD" -> 2;
            default -> 3;
        };
    }
}
