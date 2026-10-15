package com.company.exam.content.service;

import com.company.exam.content.api.QuestionTaxonomyCatalog;
import com.company.exam.content.entity.Question;
import com.company.exam.content.repository.QuestionRepository;
import com.company.exam.content.repository.QuestionVersionRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link QuestionTaxonomyCatalog} ({@code specs/17-P8-Analytics-V1.5.md §30, §37, §74}). */
@Service
public class QuestionTaxonomyCatalogService implements QuestionTaxonomyCatalog {

    private final QuestionVersionRepository versionRepository;
    private final QuestionRepository questionRepository;

    public QuestionTaxonomyCatalogService(QuestionVersionRepository versionRepository,
            QuestionRepository questionRepository) {
        this.versionRepository = versionRepository;
        this.questionRepository = questionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UUID> questionIdByVersionId(Collection<UUID> questionVersionIds) {
        if (questionVersionIds.isEmpty()) {
            return Map.of();
        }
        return versionRepository.findAllById(questionVersionIds).stream()
                .collect(Collectors.toMap(version -> version.getId(), version -> version.getQuestionId(),
                        (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<UUID>> versionIdsByQuestionId(Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<UUID>> grouped = new java.util.HashMap<>();
        for (var version : versionRepository.findByQuestionIdIn(questionIds)) {
            grouped.computeIfAbsent(version.getQuestionId(), key -> new java.util.ArrayList<>()).add(version.getId());
        }
        return grouped;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxonomyView> taxonomy(Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) {
            return List.of();
        }
        return questionRepository.findAllByIdIn(questionIds).stream()
                .map(this::toView)
                .toList();
    }

    private TaxonomyView toView(Question question) {
        return new TaxonomyView(question.getId(), question.getExamId(), question.getSubjectId(),
                question.getChapterId(), question.getTopicId(), question.getSubTopicId());
    }
}
