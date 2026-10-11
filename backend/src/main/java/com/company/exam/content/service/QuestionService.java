package com.company.exam.content.service;

import com.company.exam.academic.api.AcademicCatalog;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.content.dto.ContentDtos;
import com.company.exam.content.entity.Question;
import com.company.exam.content.entity.QuestionTag;
import com.company.exam.content.entity.QuestionTemplate;
import com.company.exam.content.entity.QuestionVersion;
import com.company.exam.content.entity.ReviewStatus;
import com.company.exam.content.mapper.ContentMapper;
import com.company.exam.content.rendering.ContentValidator;
import com.company.exam.content.repository.QuestionRepository;
import com.company.exam.content.repository.QuestionTagRepository;
import com.company.exam.content.repository.QuestionTemplateRepository;
import com.company.exam.content.repository.QuestionVersionRepository;
import com.company.exam.content.template.QuestionTypeRegistry;
import com.company.exam.content.validation.ContentTextNormalizer;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logical question workflows ({@code specs/12-P3-Content-V1.5.md §73, §87-§89, §97}).
 *
 * <p>Question creation creates the logical question and its initial version atomically. Taxonomy is
 * verified through the Academic contract, never by reaching into Academic repositories.
 */
@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository versionRepository;
    private final QuestionTagRepository questionTagRepository;
    private final QuestionTemplateRepository templateRepository;
    private final QuestionTypeRegistry typeRegistry;
    private final ContentValidator contentValidator;
    private final AcademicCatalog academicCatalog;
    private final ContentTextNormalizer normalizer;
    private final ContentMapper mapper;
    private final SecurityEventLogger audit;

    public QuestionService(QuestionRepository questionRepository, QuestionVersionRepository versionRepository,
            QuestionTagRepository questionTagRepository, QuestionTemplateRepository templateRepository,
            QuestionTypeRegistry typeRegistry, ContentValidator contentValidator, AcademicCatalog academicCatalog,
            ContentTextNormalizer normalizer, ContentMapper mapper, SecurityEventLogger audit) {
        this.questionRepository = questionRepository;
        this.versionRepository = versionRepository;
        this.questionTagRepository = questionTagRepository;
        this.templateRepository = templateRepository;
        this.typeRegistry = typeRegistry;
        this.contentValidator = contentValidator;
        this.academicCatalog = academicCatalog;
        this.normalizer = normalizer;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional
    public ContentDtos.QuestionResponse create(ContentDtos.QuestionCreateRequest request, UUID actorId) {
        QuestionTemplate template = requireTemplate(request.templateId());
        typeRegistry.require(template.getCode());
        validateTaxonomy(request.examId(), request.subjectId(), request.chapterId(), request.topicId(),
                request.subTopicId());
        academicCatalog.requireLanguage(request.languageId());
        contentValidator.validate(request.content(), "content");

        Question question = new Question();
        question.setTemplateId(request.templateId());
        question.setExamId(request.examId());
        question.setSubjectId(request.subjectId());
        question.setChapterId(request.chapterId());
        question.setTopicId(request.topicId());
        question.setSubTopicId(request.subTopicId());
        question.setStatus(Status.ACTIVE);
        Question savedQuestion = questionRepository.save(question);

        QuestionVersion version = new QuestionVersion();
        version.setQuestionId(savedQuestion.getId());
        version.setVersion(1);
        version.setTitle(normalizer.normalizeOptional(request.title()));
        version.setContent(request.content());
        version.setMarks(request.marks());
        version.setNegativeMarks(request.negativeMarks());
        version.setDifficulty(request.difficulty());
        version.setLanguageId(request.languageId());
        version.setPublished(false);
        version.setReviewStatus(ReviewStatus.DRAFT);
        versionRepository.save(version);

        audit.audit(actorId, "CREATE", "Question", savedQuestion.getId(), "Created question with initial version");
        return mapper.toResponse(savedQuestion, List.of(version), List.of());
    }

    @Transactional(readOnly = true)
    public ContentDtos.QuestionResponse get(UUID id) {
        Question question = require(id);
        List<QuestionVersion> versions = versionRepository.findByQuestionIdOrderByVersionAsc(id);
        List<UUID> tagIds = questionTagRepository.findByQuestionId(id).stream()
                .filter(tag -> tag.getStatus() == Status.ACTIVE)
                .map(QuestionTag::getTagId)
                .toList();
        return mapper.toResponse(question, versions, tagIds);
    }

    @Transactional(readOnly = true)
    public Page<ContentDtos.QuestionListItem> list(Status status, UUID templateId, UUID examId, UUID subjectId,
            UUID chapterId, UUID topicId, UUID tagId, UUID languageId, com.company.exam.content.entity.Difficulty difficulty,
            Boolean isPublished, ReviewStatus reviewStatus, String search, Pageable pageable) {
        Page<Question> page = questionRepository.findAll(
                specification(status, templateId, examId, subjectId, chapterId, topicId, tagId, languageId,
                        difficulty, isPublished, reviewStatus, search),
                pageable);
        Map<UUID, QuestionVersion> latest = latestVersions(page.getContent());
        return page.map(question -> mapper.toListItem(question, latest.get(question.getId())));
    }

    @Transactional
    public ContentDtos.QuestionResponse update(UUID id, ContentDtos.QuestionUpdateRequest request, UUID actorId) {
        Question question = require(id);
        if (request.templateId() != null) {
            QuestionTemplate template = requireTemplate(request.templateId());
            typeRegistry.require(template.getCode());
            question.setTemplateId(request.templateId());
        }
        if (request.examId() != null) {
            academicCatalog.requireExam(request.examId());
            question.setExamId(request.examId());
        }
        if (request.subjectId() != null) {
            academicCatalog.requireSubject(request.subjectId());
            question.setSubjectId(request.subjectId());
        }
        if (request.chapterId() != null) {
            academicCatalog.requireChapter(request.chapterId());
            question.setChapterId(request.chapterId());
        }
        if (request.topicId() != null) {
            academicCatalog.requireTopic(request.topicId());
            question.setTopicId(request.topicId());
        }
        if (request.subTopicId() != null) {
            academicCatalog.requireSubTopic(request.subTopicId());
            question.setSubTopicId(request.subTopicId());
        }
        if (request.status() != null) {
            if (request.status() == Status.ARCHIVED) {
                throw new InvalidStateException("Use the archive operation to archive a question");
            }
            question.setStatus(request.status());
        }
        questionRepository.save(question);

        if (request.tagIds() != null) {
            replaceTags(question.getId(), request.tagIds());
        }
        audit.audit(actorId, "UPDATE", "Question", question.getId(), "Updated question metadata");
        return get(id);
    }

    @Transactional
    public void archive(UUID id, UUID actorId) {
        Question question = require(id);
        question.setStatus(Status.ARCHIVED);
        questionRepository.save(question);
        audit.audit(actorId, "ARCHIVE", "Question", id, "Archived question");
    }

    @Transactional(readOnly = true)
    public Question require(UUID id) {
        return questionRepository.findById(id).orElseThrow(() -> NotFoundException.of("Question", id));
    }

    /** Replaces a question's tag associations, verifying each tag through the Academic contract. */
    @Transactional
    public void replaceTags(UUID questionId, List<UUID> tagIds) {
        require(questionId);
        for (UUID tagId : tagIds) {
            academicCatalog.requireTag(tagId);
        }
        List<QuestionTag> existing = questionTagRepository.findByQuestionId(questionId);
        for (QuestionTag association : existing) {
            if (association.getStatus() == Status.ACTIVE && !tagIds.contains(association.getTagId())) {
                association.setStatus(Status.INACTIVE);
                questionTagRepository.save(association);
            }
        }
        for (UUID tagId : tagIds) {
            QuestionTag association = questionTagRepository.findByQuestionIdAndTagId(questionId, tagId)
                    .orElseGet(() -> {
                        QuestionTag created = new QuestionTag();
                        created.setQuestionId(questionId);
                        created.setTagId(tagId);
                        return created;
                    });
            association.setStatus(Status.ACTIVE);
            questionTagRepository.save(association);
        }
    }

    private QuestionTemplate requireTemplate(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Question template", id));
    }

    private void validateTaxonomy(UUID examId, UUID subjectId, UUID chapterId, UUID topicId, UUID subTopicId) {
        academicCatalog.requireExam(examId);
        academicCatalog.requireSubject(subjectId);
        if (chapterId != null) {
            academicCatalog.requireChapter(chapterId);
        }
        if (topicId != null) {
            academicCatalog.requireTopic(topicId);
        }
        if (subTopicId != null) {
            academicCatalog.requireSubTopic(subTopicId);
        }
    }

    private Map<UUID, QuestionVersion> latestVersions(List<Question> questions) {
        if (questions.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = questions.stream().map(Question::getId).toList();
        return versionRepository.findByQuestionIdIn(ids).stream()
                .collect(Collectors.groupingBy(QuestionVersion::getQuestionId,
                        Collectors.collectingAndThen(Collectors.maxBy(Comparator.comparing(QuestionVersion::getVersion)),
                                optional -> optional.orElse(null))));
    }

    private Specification<Question> specification(Status status, UUID templateId, UUID examId, UUID subjectId,
            UUID chapterId, UUID topicId, UUID tagId, UUID languageId, com.company.exam.content.entity.Difficulty difficulty,
            Boolean isPublished, ReviewStatus reviewStatus, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (templateId != null) {
                predicates.add(cb.equal(root.get("templateId"), templateId));
            }
            if (examId != null) {
                predicates.add(cb.equal(root.get("examId"), examId));
            }
            if (subjectId != null) {
                predicates.add(cb.equal(root.get("subjectId"), subjectId));
            }
            if (chapterId != null) {
                predicates.add(cb.equal(root.get("chapterId"), chapterId));
            }
            if (topicId != null) {
                predicates.add(cb.equal(root.get("topicId"), topicId));
            }
            if (tagId != null) {
                Subquery<UUID> tagSub = query.subquery(UUID.class);
                Root<QuestionTag> tag = tagSub.from(QuestionTag.class);
                tagSub.select(tag.get("questionId")).where(
                        cb.equal(tag.get("tagId"), tagId),
                        cb.equal(tag.get("status"), Status.ACTIVE),
                        cb.equal(tag.get("questionId"), root.get("id")));
                predicates.add(cb.exists(tagSub));
            }
            if (languageId != null || difficulty != null || isPublished != null || reviewStatus != null
                    || (search != null && !search.isBlank())) {
                Subquery<UUID> versionSub = query.subquery(UUID.class);
                Root<QuestionVersion> version = versionSub.from(QuestionVersion.class);
                List<Predicate> versionPredicates = new ArrayList<>();
                versionPredicates.add(cb.equal(version.get("questionId"), root.get("id")));
                if (languageId != null) {
                    versionPredicates.add(cb.equal(version.get("languageId"), languageId));
                }
                if (difficulty != null) {
                    versionPredicates.add(cb.equal(version.get("difficulty"), difficulty));
                }
                if (isPublished != null) {
                    versionPredicates.add(cb.equal(version.get("published"), isPublished));
                }
                if (reviewStatus != null) {
                    versionPredicates.add(cb.equal(version.get("reviewStatus"), reviewStatus));
                }
                if (search != null && !search.isBlank()) {
                    String like = "%" + search.trim().toLowerCase() + "%";
                    versionPredicates.add(cb.or(
                            cb.like(cb.lower(version.get("title")), like),
                            cb.like(cb.lower(version.get("content")), like)));
                }
                versionSub.select(version.get("questionId"))
                        .where(versionPredicates.toArray(new Predicate[0]));
                predicates.add(cb.exists(versionSub));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
