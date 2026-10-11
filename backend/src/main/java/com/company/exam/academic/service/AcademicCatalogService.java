package com.company.exam.academic.service;

import com.company.exam.academic.api.AcademicCatalog;
import com.company.exam.academic.repository.ChapterRepository;
import com.company.exam.academic.repository.ExamRepository;
import com.company.exam.academic.repository.LanguageRepository;
import com.company.exam.academic.repository.SubTopicRepository;
import com.company.exam.academic.repository.SubjectRepository;
import com.company.exam.academic.repository.TagRepository;
import com.company.exam.academic.repository.TopicRepository;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link AcademicCatalog}: resolves and verifies Academic master references for other
 * modules without exposing Academic repositories ({@code specs/12-P3-Content-V1.5.md §81}).
 */
@Service
public class AcademicCatalogService implements AcademicCatalog {

    private final ExamRepository examRepository;
    private final SubjectRepository subjectRepository;
    private final ChapterRepository chapterRepository;
    private final TopicRepository topicRepository;
    private final SubTopicRepository subTopicRepository;
    private final LanguageRepository languageRepository;
    private final TagRepository tagRepository;

    public AcademicCatalogService(ExamRepository examRepository, SubjectRepository subjectRepository,
            ChapterRepository chapterRepository, TopicRepository topicRepository,
            SubTopicRepository subTopicRepository, LanguageRepository languageRepository,
            TagRepository tagRepository) {
        this.examRepository = examRepository;
        this.subjectRepository = subjectRepository;
        this.chapterRepository = chapterRepository;
        this.topicRepository = topicRepository;
        this.subTopicRepository = subTopicRepository;
        this.languageRepository = languageRepository;
        this.tagRepository = tagRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicRef requireExam(UUID id) {
        var exam = examRepository.findById(id).orElseThrow(() -> NotFoundException.of("Exam", id));
        return new AcademicRef(exam.getId(), exam.getName(), exam.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicRef requireSubject(UUID id) {
        var subject = subjectRepository.findById(id).orElseThrow(() -> NotFoundException.of("Subject", id));
        return new AcademicRef(subject.getId(), subject.getName(), subject.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicRef requireChapter(UUID id) {
        var chapter = chapterRepository.findById(id).orElseThrow(() -> NotFoundException.of("Chapter", id));
        return new AcademicRef(chapter.getId(), chapter.getName(), chapter.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicRef requireTopic(UUID id) {
        var topic = topicRepository.findById(id).orElseThrow(() -> NotFoundException.of("Topic", id));
        return new AcademicRef(topic.getId(), topic.getName(), topic.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicRef requireSubTopic(UUID id) {
        var subTopic = subTopicRepository.findById(id).orElseThrow(() -> NotFoundException.of("SubTopic", id));
        return new AcademicRef(subTopic.getId(), subTopic.getName(), subTopic.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicRef requireLanguage(UUID id) {
        var language = languageRepository.findById(id).orElseThrow(() -> NotFoundException.of("Language", id));
        return new AcademicRef(language.getId(), language.getName(), language.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicRef requireTag(UUID id) {
        var tag = tagRepository.findById(id).orElseThrow(() -> NotFoundException.of("Tag", id));
        return new AcademicRef(tag.getId(), tag.getName(), tag.getStatus());
    }

    /** Convenience: true when the reference exists and is ACTIVE. */
    public boolean isActive(AcademicRef ref) {
        return ref != null && ref.status() == Status.ACTIVE;
    }
}
