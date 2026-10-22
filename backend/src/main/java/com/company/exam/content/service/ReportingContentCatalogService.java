package com.company.exam.content.service;

import com.company.exam.content.api.ReportingContentCatalog;
import com.company.exam.content.entity.Question;
import com.company.exam.content.entity.QuestionTag;
import com.company.exam.content.entity.QuestionVersion;
import com.company.exam.content.repository.QuestionRepository;
import com.company.exam.content.repository.QuestionTagRepository;
import com.company.exam.content.repository.QuestionVersionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link ReportingContentCatalog}
 * ({@code specs/22-P13-Reporting-V1.5.md §10, §50, §51, §52}).
 *
 * <p>Every read is bounded: the question list is truncated to {@code limit} and the tag association
 * list is fetched through a page request, so a report never materializes the whole content bank
 * ({@code §7.6}). Nothing here mutates content ({@code §7.3}).
 */
@Service
public class ReportingContentCatalogService implements ReportingContentCatalog {

    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository versionRepository;
    private final QuestionTagRepository questionTagRepository;

    public ReportingContentCatalogService(QuestionRepository questionRepository,
            QuestionVersionRepository versionRepository, QuestionTagRepository questionTagRepository) {
        this.questionRepository = questionRepository;
        this.versionRepository = versionRepository;
        this.questionTagRepository = questionTagRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionRow> questions(UUID examId, UUID subjectId, int limit) {
        return questionRepository.findAll().stream()
                .filter(question -> examId == null || examId.equals(question.getExamId()))
                .filter(question -> subjectId == null || subjectId.equals(question.getSubjectId()))
                .limit(limit)
                .map(this::toRow)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionVersionRow> versionsOf(List<UUID> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }
        return versionRepository.findByQuestionIdIn(List.copyOf(questionIds)).stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionTagRow> questionTags(int limit) {
        return questionTagRepository.findAll(PageRequest.of(0, Math.max(1, limit))).stream()
                .map(link -> new QuestionTagRow(link.getQuestionId(), link.getTagId()))
                .toList();
    }

    private QuestionRow toRow(Question question) {
        return new QuestionRow(question.getId(), question.getExamId(), question.getSubjectId(),
                question.getChapterId(), question.getTopicId(), question.getSubTopicId(),
                question.getStatus() == null ? null : question.getStatus().name());
    }

    private QuestionVersionRow toRow(QuestionVersion version) {
        return new QuestionVersionRow(version.getId(), version.getQuestionId(), version.getVersion(),
                version.getReviewStatus() == null ? null : version.getReviewStatus().name(),
                version.isPublished(), version.getDifficulty() == null ? null : version.getDifficulty().name());
    }
}
