package com.company.exam.integration.content;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.academic.entity.Exam;
import com.company.exam.academic.entity.Language;
import com.company.exam.academic.entity.Subject;
import com.company.exam.academic.repository.ExamRepository;
import com.company.exam.academic.repository.LanguageRepository;
import com.company.exam.academic.repository.SubjectRepository;
import com.company.exam.common.enums.Status;
import com.company.exam.content.repository.QuestionTemplateRepository;
import com.company.exam.integration.identity.AbstractIdentityIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Content authoring, review, publication, and immutability
 * ({@code specs/12-P3-Content-V1.5.md §139, §164, §165}).
 */
class ContentApiIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private ExamRepository examRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private LanguageRepository languageRepository;
    @Autowired
    private QuestionTemplateRepository templateRepository;

    // ----- Journey: create -> options -> submit -> approve -> publish -----

    @Test
    void theAuthoringJourneyPublishesAVersion() throws Exception {
        String staff = staffToken();
        String reviewer = reviewerToken();
        String admin = adminToken();

        String questionId = createSingleChoiceQuestion(staff);
        String versionId = firstVersionId(staff, questionId);
        addOption(staff, questionId, versionId, "A", 1, true);
        addOption(staff, questionId, versionId, "B", 2, false);

        submitReview(staff, questionId, versionId).andExpect(status().isOk());

        // A different reviewer approves; the author cannot self-approve (covered separately).
        approve(reviewer, questionId, versionId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"));

        publish(admin, questionId, versionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(true))
                .andExpect(jsonPath("$.data.reviewStatus").value("PUBLISHED"));

        // The rendered canonical content is returned by the version read model.
        mockMvc.perform(get("/api/v1/questions/" + questionId + "/versions/" + versionId)
                        .header(AUTHORIZATION, bearer(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentFormat").value("MARKDOWN_LATEX_V1"))
                .andExpect(jsonPath("$.data.renderedContent").isNotEmpty());
    }

    @Test
    void selfReviewIsBlocked() throws Exception {
        // ADMIN holds both submit and review permissions.
        String admin = adminToken();
        String questionId = createSingleChoiceQuestion(admin);
        String versionId = firstVersionId(admin, questionId);
        addOption(admin, questionId, versionId, "A", 1, true);
        addOption(admin, questionId, versionId, "B", 2, false);

        submitReview(admin, questionId, versionId).andExpect(status().isOk());
        approve(admin, questionId, versionId).andExpect(status().isForbidden());
    }

    @Test
    void illegalReviewTransitionsAreRejected() throws Exception {
        String staff = staffToken();
        String reviewer = reviewerToken();
        String questionId = createSingleChoiceQuestion(staff);
        String versionId = firstVersionId(staff, questionId);

        // Cannot approve a DRAFT version.
        approve(reviewer, questionId, versionId).andExpect(status().isConflict());

        // With only one correct option, submitting requires options; add them then submit twice.
        addOption(staff, questionId, versionId, "A", 1, true);
        addOption(staff, questionId, versionId, "B", 2, false);
        submitReview(staff, questionId, versionId).andExpect(status().isOk());
        submitReview(staff, questionId, versionId).andExpect(status().isConflict());
    }

    @Test
    void aPublishedVersionIsImmutable() throws Exception {
        String staff = staffToken();
        String reviewer = reviewerToken();
        String admin = adminToken();

        String questionId = createSingleChoiceQuestion(staff);
        String versionId = firstVersionId(staff, questionId);
        String optionId = addOption(staff, questionId, versionId, "A", 1, true);
        addOption(staff, questionId, versionId, "B", 2, false);
        submitReview(staff, questionId, versionId).andExpect(status().isOk());
        approve(reviewer, questionId, versionId).andExpect(status().isOk());
        publish(admin, questionId, versionId).andExpect(status().isOk());

        // Every mutation against the published version is rejected.
        mockMvc.perform(put("/api/v1/questions/" + questionId + "/versions/" + versionId + "/options/" + optionId)
                        .header(AUTHORIZATION, bearer(staff)).contentType(JSON)
                        .content("{\"content\":\"changed\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/questions/" + questionId + "/versions/" + versionId + "/options")
                        .header(AUTHORIZATION, bearer(staff)).contentType(JSON)
                        .content("{\"optionKey\":\"C\",\"content\":\"c\",\"displayOrder\":3,\"isCorrect\":false}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/question-versions/" + versionId + "/explanation")
                        .header(AUTHORIZATION, bearer(staff)).contentType(JSON)
                        .content("{\"content\":\"because\"}"))
                .andExpect(status().isConflict());

        // Corrections require a new version.
        mockMvc.perform(post("/api/v1/questions/" + questionId + "/versions")
                        .header(AUTHORIZATION, bearer(staff)).contentType(JSON)
                        .content(versionBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.reviewStatus").value("DRAFT"));
    }

    @Test
    void publishIsRejectedBeforeApproval() throws Exception {
        String staff = staffToken();
        String admin = adminToken();
        String questionId = createSingleChoiceQuestion(staff);
        String versionId = firstVersionId(staff, questionId);
        addOption(staff, questionId, versionId, "A", 1, true);
        addOption(staff, questionId, versionId, "B", 2, false);

        publish(admin, questionId, versionId).andExpect(status().isConflict());
    }

    @Test
    void publishIsRejectedWhenTheTemplateRulesAreNotMet() throws Exception {
        String staff = staffToken();
        String reviewer = reviewerToken();
        String admin = adminToken();
        String questionId = createSingleChoiceQuestion(staff);
        String versionId = firstVersionId(staff, questionId);
        // Two options but both correct -> SINGLE_CHOICE requires exactly one correct.
        addOption(staff, questionId, versionId, "A", 1, true);
        addOption(staff, questionId, versionId, "B", 2, true);

        submitReview(staff, questionId, versionId).andExpect(status().isOk());
        approve(reviewer, questionId, versionId).andExpect(status().isOk());
        publish(admin, questionId, versionId).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void contentReferencingAnotherVersionAssetIsRejected() throws Exception {
        String staff = staffToken();
        String questionOne = createSingleChoiceQuestion(staff);
        String versionOne = firstVersionId(staff, questionOne);
        String assetId = uploadAsset(staff, versionOne);

        String questionTwo = createSingleChoiceQuestion(staff);
        String versionTwo = firstVersionId(staff, questionTwo);

        mockMvc.perform(post("/api/v1/questions/" + questionTwo + "/versions/" + versionTwo + "/options")
                        .header(AUTHORIZATION, bearer(staff)).contentType(JSON)
                        .content("{\"optionKey\":\"A\",\"content\":\"see [asset: " + assetId
                                + "]\",\"displayOrder\":1,\"isCorrect\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ASSET_VERSION_MISMATCH"));
    }

    @Test
    void taxonomyMustExistWhenCreatingAQuestion() throws Exception {
        String staff = staffToken();

        mockMvc.perform(post("/api/v1/questions").header(AUTHORIZATION, bearer(staff)).contentType(JSON)
                        .content(questionBody(templateId("SINGLE_CHOICE"), UUID.randomUUID(), randomSubjectId(),
                                randomLanguageId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aStudentCannotAccessTheQuestionBank() throws Exception {
        String student = tokenFor(createUser(syntheticEmail("content-student"), PASSWORD, "STUDENT"));

        mockMvc.perform(get("/api/v1/questions").header(AUTHORIZATION, bearer(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    void authoringOptionResponseExposesIsCorrectButStudentProjectionDoesNot() throws Exception {
        // The authoring API legitimately returns isCorrect (reviewed with reviewers); the leakage
        // contract is asserted in ContentLeakageTest for the student projection.
        String staff = staffToken();
        String questionId = createSingleChoiceQuestion(staff);
        String versionId = firstVersionId(staff, questionId);
        addOption(staff, questionId, versionId, "A", 1, true);

        mockMvc.perform(get("/api/v1/questions/" + questionId + "/versions/" + versionId + "/options")
                        .header(AUTHORIZATION, bearer(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isCorrect").value(true));
    }

    // ----- helpers -----

    private String adminToken() {
        return tokenFor(createUser(syntheticEmail("content-admin"), PASSWORD, "ADMIN"));
    }

    private String staffToken() {
        return tokenFor(createUser(syntheticEmail("content-staff"), PASSWORD, "STAFF"));
    }

    private String reviewerToken() {
        return tokenFor(createUser(syntheticEmail("content-reviewer"), PASSWORD, "REVIEWER"));
    }

    private UUID templateId(String code) {
        return templateRepository.findByCode(code).orElseThrow().getId();
    }

    private UUID randomSubjectId() {
        return createSubject(createExam());
    }

    private UUID randomLanguageId() {
        return createLanguage();
    }

    private UUID createExam() {
        Exam exam = new Exam();
        exam.setCode("EX-" + UUID.randomUUID());
        exam.setName("Exam");
        exam.setStatus(Status.ACTIVE);
        return examRepository.save(exam).getId();
    }

    private UUID createSubject(UUID examId) {
        Subject subject = new Subject();
        subject.setExamId(examId);
        subject.setCode("SUB-" + UUID.randomUUID());
        subject.setName("Subject");
        subject.setStatus(Status.ACTIVE);
        return subjectRepository.save(subject).getId();
    }

    private UUID createLanguage() {
        Language language = new Language();
        language.setCode("L" + UUID.randomUUID().toString().substring(0, 6));
        language.setName("Language");
        language.setStatus(Status.ACTIVE);
        return languageRepository.save(language).getId();
    }

    private String createSingleChoiceQuestion(String token) throws Exception {
        String examId = createExam().toString();
        String subjectId = createSubject(UUID.fromString(examId)).toString();
        String languageId = createLanguage().toString();
        String body = mockMvc.perform(post("/api/v1/questions").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content(questionBody(templateId("SINGLE_CHOICE"), UUID.fromString(examId),
                                UUID.fromString(subjectId), UUID.fromString(languageId))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String firstVersionId(String token, String questionId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/questions/" + questionId + "/versions")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data[0].id");
    }

    private String addOption(String token, String questionId, String versionId, String key, int order, boolean correct)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/questions/" + questionId + "/versions/" + versionId + "/options")
                        .header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"optionKey\":\"" + key + "\",\"content\":\"option " + key
                                + "\",\"displayOrder\":" + order + ",\"isCorrect\":" + correct + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private org.springframework.test.web.servlet.ResultActions submitReview(String token, String questionId,
            String versionId) throws Exception {
        return mockMvc.perform(post("/api/v1/questions/" + questionId + "/versions/" + versionId + "/submit-review")
                .header(AUTHORIZATION, bearer(token)));
    }

    private org.springframework.test.web.servlet.ResultActions approve(String token, String questionId, String versionId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/questions/" + questionId + "/versions/" + versionId + "/approve")
                .header(AUTHORIZATION, bearer(token)).contentType(JSON).content("{\"remarks\":\"ok\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions publish(String token, String questionId, String versionId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/questions/" + questionId + "/versions/" + versionId + "/publish")
                .header(AUTHORIZATION, bearer(token)));
    }

    private String uploadAsset(String token, String versionId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "diagram.png", "image/png",
                new byte[] {1, 2, 3, 4});
        String body = mockMvc.perform(multipart("/api/v1/question-versions/" + versionId + "/assets")
                        .file(file).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String questionBody(UUID templateId, UUID examId, UUID subjectId, UUID languageId) {
        return "{\"templateId\":\"" + templateId + "\",\"examId\":\"" + examId + "\",\"subjectId\":\"" + subjectId
                + "\",\"title\":\"Example\",\"content\":\"What is 2+2?\",\"marks\":2,\"negativeMarks\":0,"
                + "\"difficulty\":\"MEDIUM\",\"languageId\":\"" + languageId + "\"}";
    }

    private String versionBody() {
        return "{\"title\":\"Corrected\",\"content\":\"What is 3+3?\",\"marks\":2,\"negativeMarks\":0,"
                + "\"difficulty\":\"MEDIUM\",\"languageId\":\"" + createLanguage() + "\"}";
    }
}
