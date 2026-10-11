package com.company.exam.content.mapper;

import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.entity.Asset;
import com.company.exam.content.entity.Explanation;
import com.company.exam.content.entity.Hint;
import com.company.exam.content.entity.PreviousExamHistory;
import com.company.exam.content.entity.Question;
import com.company.exam.content.entity.QuestionOption;
import com.company.exam.content.entity.QuestionTemplate;
import com.company.exam.content.entity.QuestionVersion;
import com.company.exam.content.rendering.MarkdownLatexRenderer;
import com.company.exam.content.rendering.RenderedContent;
import com.company.exam.content.template.QuestionTemplateConfigurationParser;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Explicit entity-to-DTO mapping for Content ({@code specs/12-P3-Content-V1.5.md §83, §92}).
 *
 * <p>Authoring responses may include {@code isCorrect}; the student projection never does. Raw
 * asset file paths are never exposed.
 */
@Component
public class ContentMapper {

    private final MarkdownLatexRenderer renderer;
    private final QuestionTemplateConfigurationParser templateConfigurationParser;

    public ContentMapper(MarkdownLatexRenderer renderer,
            QuestionTemplateConfigurationParser templateConfigurationParser) {
        this.renderer = renderer;
        this.templateConfigurationParser = templateConfigurationParser;
    }

    public ContentDtos.TemplateResponse toResponse(QuestionTemplate template) {
        boolean requiresLatex = templateConfigurationParser.parse(template).requiresLatexParsing();
        return new ContentDtos.TemplateResponse(template.getId(), template.getCode(), template.getName(),
                template.getDescription(), requiresLatex, template.getStatus());
    }

    public ContentDtos.VersionSummary toSummary(QuestionVersion version) {
        return new ContentDtos.VersionSummary(version.getId(), version.getVersion(), version.getReviewStatus(),
                version.isPublished(), version.getMarks(), version.getNegativeMarks(), version.getDifficulty(),
                version.getPublishedOn());
    }

    public ContentDtos.QuestionResponse toResponse(Question question, List<QuestionVersion> versions,
            List<java.util.UUID> tagIds) {
        return new ContentDtos.QuestionResponse(question.getId(), question.getTemplateId(), question.getExamId(),
                question.getSubjectId(), question.getChapterId(), question.getTopicId(), question.getSubTopicId(),
                question.getStatus(), versions.stream().map(this::toSummary).toList(), List.copyOf(tagIds));
    }

    public ContentDtos.QuestionListItem toListItem(Question question, QuestionVersion latest) {
        return new ContentDtos.QuestionListItem(question.getId(), question.getTemplateId(), question.getExamId(),
                question.getSubjectId(), latest == null ? null : latest.getDifficulty(),
                latest == null ? null : latest.getVersion(),
                latest == null ? null : latest.getReviewStatus(),
                latest != null && latest.isPublished(), question.getUpdatedOn());
    }

    public ContentDtos.VersionResponse toResponse(QuestionVersion version) {
        RenderedContent rendered = renderer.render(version.getContent());
        return new ContentDtos.VersionResponse(version.getId(), version.getQuestionId(), version.getVersion(),
                version.getTitle(), version.getContent(), MarkdownLatexRenderer.CONTENT_FORMAT, rendered.html(),
                version.getMarks(), version.getNegativeMarks(), version.getDifficulty(), version.getLanguageId(),
                version.isPublished(), version.getReviewStatus(), version.getPublishedBy(), version.getPublishedOn(),
                version.getReviewedBy(), version.getReviewedOn(), version.getReviewRemarks());
    }

    public ContentDtos.VersionListItem toListItem(QuestionVersion version) {
        return new ContentDtos.VersionListItem(version.getId(), version.getVersion(), version.getReviewStatus(),
                version.isPublished(), version.getMarks(), version.getNegativeMarks(), version.getDifficulty(),
                version.getPublishedOn());
    }

    public ContentDtos.OptionResponse toResponse(QuestionOption option) {
        return new ContentDtos.OptionResponse(option.getId(), option.getQuestionVersionId(), option.getOptionKey(),
                option.getContent(), option.getDisplayOrder(), option.isCorrect());
    }

    /** Student-safe option view: deliberately omits {@code isCorrect}. */
    public ContentDtos.StudentOptionView toStudentView(QuestionOption option) {
        return new ContentDtos.StudentOptionView(option.getId(), option.getOptionKey(), option.getDisplayOrder(),
                option.getContent(), MarkdownLatexRenderer.CONTENT_FORMAT);
    }

    /**
     * Builds a student-safe question view from a version that DOES contain correctness data,
     * proving the projection never leaks it ({@code specs/12-P3-Content-V1.5.md §127}).
     */
    public ContentDtos.StudentQuestionView toStudentView(QuestionVersion version, List<QuestionOption> options,
            boolean requiresLatexParsing) {
        RenderedContent rendered = renderer.render(version.getContent());
        return new ContentDtos.StudentQuestionView(version.getId(), MarkdownLatexRenderer.CONTENT_FORMAT,
                requiresLatexParsing, version.getContent(), rendered.html(), version.getMarks(),
                version.getNegativeMarks(), options.stream().map(this::toStudentView).toList());
    }

    public ContentDtos.AssetResponse toResponse(Asset asset) {
        return new ContentDtos.AssetResponse(asset.getId(), asset.getQuestionVersionId(), asset.getFileName(),
                asset.getFileType(), asset.getStatus());
    }

    public ContentDtos.ExplanationResponse toResponse(Explanation explanation) {
        RenderedContent rendered = renderer.render(explanation.getContent());
        return new ContentDtos.ExplanationResponse(explanation.getId(), explanation.getQuestionVersionId(),
                explanation.getContent(), MarkdownLatexRenderer.CONTENT_FORMAT, rendered.html());
    }

    public ContentDtos.HintResponse toResponse(Hint hint) {
        RenderedContent rendered = renderer.render(hint.getContent());
        return new ContentDtos.HintResponse(hint.getId(), hint.getQuestionVersionId(), hint.getContent(),
                MarkdownLatexRenderer.CONTENT_FORMAT, rendered.html());
    }

    public ContentDtos.PreviousExamResponse toResponse(PreviousExamHistory history) {
        return new ContentDtos.PreviousExamResponse(history.getId(), history.getQuestionId(), history.getExamId(),
                history.getYear(), history.getShift(), history.getSource());
    }
}
