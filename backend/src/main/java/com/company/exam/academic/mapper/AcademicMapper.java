package com.company.exam.academic.mapper;

import com.company.exam.academic.dto.AcademicDtos;
import com.company.exam.academic.entity.Chapter;
import com.company.exam.academic.entity.Exam;
import com.company.exam.academic.entity.Language;
import com.company.exam.academic.entity.SubTopic;
import com.company.exam.academic.entity.Subject;
import com.company.exam.academic.entity.Tag;
import com.company.exam.academic.entity.Topic;
import org.springframework.stereotype.Component;

/**
 * Explicit entity-to-DTO mapping for Academic ({@code specs/11-P2-Academic-V1.5.md §46}).
 *
 * <p>A single explicit mapper keeps the entity/DTO boundary in one reviewable place; audit
 * implementation details are not exposed in response DTOs.
 */
@Component
public class AcademicMapper {

    public AcademicDtos.ExamResponse toResponse(Exam exam) {
        return new AcademicDtos.ExamResponse(exam.getId(), exam.getCode(), exam.getName(),
                exam.getDescription(), exam.getStatus(), exam.getCreatedOn(), exam.getUpdatedOn());
    }

    public AcademicDtos.ExamListItem toListItem(Exam exam) {
        return new AcademicDtos.ExamListItem(exam.getId(), exam.getCode(), exam.getName(), exam.getStatus(),
                exam.getUpdatedOn());
    }

    public AcademicDtos.SubjectResponse toResponse(Subject subject) {
        return new AcademicDtos.SubjectResponse(subject.getId(), subject.getExamId(), subject.getCode(),
                subject.getName(), subject.getDescription(), subject.getStatus(), subject.getCreatedOn(),
                subject.getUpdatedOn());
    }

    public AcademicDtos.SubjectListItem toListItem(Subject subject) {
        return new AcademicDtos.SubjectListItem(subject.getId(), subject.getExamId(), subject.getCode(),
                subject.getName(), subject.getStatus(), subject.getUpdatedOn());
    }

    public AcademicDtos.ChapterResponse toResponse(Chapter chapter) {
        return new AcademicDtos.ChapterResponse(chapter.getId(), chapter.getSubjectId(), chapter.getCode(),
                chapter.getName(), chapter.getDescription(), chapter.getStatus(), chapter.getCreatedOn(),
                chapter.getUpdatedOn());
    }

    public AcademicDtos.ChapterListItem toListItem(Chapter chapter) {
        return new AcademicDtos.ChapterListItem(chapter.getId(), chapter.getSubjectId(), chapter.getCode(),
                chapter.getName(), chapter.getStatus(), chapter.getUpdatedOn());
    }

    public AcademicDtos.TopicResponse toResponse(Topic topic) {
        return new AcademicDtos.TopicResponse(topic.getId(), topic.getChapterId(), topic.getCode(), topic.getName(),
                topic.getDescription(), topic.getStatus(), topic.getCreatedOn(), topic.getUpdatedOn());
    }

    public AcademicDtos.TopicListItem toListItem(Topic topic) {
        return new AcademicDtos.TopicListItem(topic.getId(), topic.getChapterId(), topic.getCode(), topic.getName(),
                topic.getStatus(), topic.getUpdatedOn());
    }

    public AcademicDtos.SubTopicResponse toResponse(SubTopic subTopic) {
        return new AcademicDtos.SubTopicResponse(subTopic.getId(), subTopic.getTopicId(), subTopic.getCode(),
                subTopic.getName(), subTopic.getDescription(), subTopic.getStatus(), subTopic.getCreatedOn(),
                subTopic.getUpdatedOn());
    }

    public AcademicDtos.SubTopicListItem toListItem(SubTopic subTopic) {
        return new AcademicDtos.SubTopicListItem(subTopic.getId(), subTopic.getTopicId(), subTopic.getCode(),
                subTopic.getName(), subTopic.getStatus(), subTopic.getUpdatedOn());
    }

    public AcademicDtos.LanguageResponse toResponse(Language language) {
        return new AcademicDtos.LanguageResponse(language.getId(), language.getCode(), language.getName(),
                language.getStatus(), language.getCreatedOn(), language.getUpdatedOn());
    }

    public AcademicDtos.LanguageListItem toListItem(Language language) {
        return new AcademicDtos.LanguageListItem(language.getId(), language.getCode(), language.getName(),
                language.getStatus(), language.getUpdatedOn());
    }

    public AcademicDtos.TagResponse toResponse(Tag tag) {
        return new AcademicDtos.TagResponse(tag.getId(), tag.getCode(), tag.getName(), tag.getDescription(),
                tag.getStatus(), tag.getCreatedOn(), tag.getUpdatedOn());
    }

    public AcademicDtos.TagListItem toListItem(Tag tag) {
        return new AcademicDtos.TagListItem(tag.getId(), tag.getCode(), tag.getName(), tag.getStatus(),
                tag.getUpdatedOn());
    }
}
