package com.company.exam.integration.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * P4 golden path and the three critical verifications
 * ({@code specs/13-P4-Assessment-V1.5.md §200-§203}):
 * deterministic and idempotent rule materialization, publication blocked while incomplete, and
 * engine type immutability after publication.
 */
class AssessmentApiIntegrationTest extends AbstractIdentityIntegrationTest {

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
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private com.company.exam.assessment.service.AssessmentVersionService versionService;

    // ----- Verification: deterministic and idempotent materialization (P4-12/P4-14) -----

    @Test
    void ruleMaterializationIsDeterministicAndIdempotent() throws Exception {
        UUID examId = createExam();
        UUID subjectId = createSubject(examId);
        UUID languageId = createLanguage();
        for (int i = 0; i < 3; i++) {
            publishQuestionVersion(examId, subjectId, languageId);
        }
        String admin = adminToken();

        UUID blueprintVersionId = publishBlueprintWithSection(admin, 2);
        String[] first = createAssessmentVersionWithRule(admin, blueprintVersionId, examId, 2);
        String[] second = createAssessmentVersionWithRule(admin, blueprintVersionId, examId, 2);

        materialize(first[1], admin).andExpect(status().isOk());
        materialize(second[1], admin).andExpect(status().isOk());

        Set<String> firstSet = materializedVersions(first[1], admin);
        Set<String> secondSet = materializedVersions(second[1], admin);

        // Deterministic: same pool and rules produce the same set with id-ascending tie-break.
        assertThat(firstSet).hasSize(2).isEqualTo(secondSet);

        // Idempotent: re-running leaves the set unchanged.
        materialize(second[1], admin).andExpect(status().isOk());
        assertThat(materializedVersions(second[1], admin)).isEqualTo(firstSet);
    }

    // ----- Verification: publication blocks incomplete assessments (P4-16/P4-18) -----

    @Test
    void publicationIsBlockedWhenTheAssessmentIsIncomplete() throws Exception {
        UUID examId = createExam();
        UUID subjectId = createSubject(examId);
        UUID languageId = createLanguage();
        publishQuestionVersion(examId, subjectId, languageId);
        publishQuestionVersion(examId, subjectId, languageId);
        String admin = adminToken();

        // Blueprint requires 3 questions, but only 2 can be materialized.
        UUID blueprintVersionId = publishBlueprintWithSection(admin, 3);
        String[] version = createAssessmentVersionWithRule(admin, blueprintVersionId, examId, 2);
        materialize(version[1], admin).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/assessment-versions/" + version[1] + "/validate")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors[0].code").value("QUESTION_COUNT_MISMATCH"));

        mockMvc.perform(post("/api/v1/assessment-versions/" + version[1] + "/publish")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void publicationIsBlockedWithoutAnEngineType() throws Exception {
        String admin = adminToken();
        UUID blueprintVersionId = publishBlueprintWithSection(admin, 1);
        String body = "{\"blueprintVersionId\":\"" + blueprintVersionId + "\",\"durationMinutes\":60,"
                + "\"deliveryPolicyId\":\"" + defaultDeliveryPolicyId() + "\"}";
        String assessmentId = createAssessment(admin, "EX-" + UUID.randomUUID());
        String created = mockMvc.perform(post("/api/v1/assessments/" + assessmentId + "/versions")
                        .header(AUTHORIZATION, bearer(admin)).contentType(JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String versionId = JsonPath.read(created, "$.data.id");

        mockMvc.perform(post("/api/v1/assessment-versions/" + versionId + "/validate")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false));
        mockMvc.perform(post("/api/v1/assessment-versions/" + versionId + "/publish")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ----- Verification: engine type immutable after publication (P4-18) -----

    @Test
    void engineTypeIsImmutableAfterPublication() throws Exception {
        UUID examId = createExam();
        UUID subjectId = createSubject(examId);
        UUID languageId = createLanguage();
        publishQuestionVersion(examId, subjectId, languageId);
        publishQuestionVersion(examId, subjectId, languageId);
        String admin = adminToken();

        UUID blueprintVersionId = publishBlueprintWithSection(admin, 2);
        String[] version = createAssessmentVersionWithRule(admin, blueprintVersionId, examId, 2);
        materialize(version[1], admin).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/assessment-versions/" + version[1] + "/publish")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(true));

        // The frozen contract exposes no version-mutation endpoint; immutability is enforced and
        // verified at the service boundary. Any change (including engine type) is rejected.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> versionService.update(
                        UUID.fromString(version[0]), UUID.fromString(version[1]),
                        new com.company.exam.assessment.dto.AssessmentDtos.AssessmentVersionUpdateRequest(
                                com.company.exam.assessment.entity.EngineType.NTA, null, null),
                        UUID.randomUUID()))
                .isInstanceOf(com.company.exam.common.exception.InvalidStateException.class);

        mockMvc.perform(get("/api/v1/assessment-versions/" + version[1])
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.data.engineType").value("CUSTOM"));
    }

    // ----- Golden path + historical reproducibility (P4-200, P4-202) -----

    @Test
    void theGoldenPathPublishesADeterministicAssessment() throws Exception {
        UUID examId = createExam();
        UUID subjectId = createSubject(examId);
        UUID languageId = createLanguage();
        publishQuestionVersion(examId, subjectId, languageId);
        publishQuestionVersion(examId, subjectId, languageId);
        String admin = adminToken();

        UUID blueprintVersionId = publishBlueprintWithSection(admin, 2);
        String[] version = createAssessmentVersionWithRule(admin, blueprintVersionId, examId, 2);
        materialize(version[1], admin).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/assessment-versions/" + version[1] + "/validate")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.data.valid").value(true));

        mockMvc.perform(get("/api/v1/assessment-versions/" + version[1] + "/preview")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections").isArray());

        mockMvc.perform(post("/api/v1/assessment-versions/" + version[1] + "/publish")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.data.published").value(true))
                .andExpect(jsonPath("$.data.totalQuestions").value(2));

        // Historical reproducibility: the published version still binds the exact blueprint version.
        mockMvc.perform(get("/api/v1/assessment-versions/" + version[1])
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.data.blueprintVersionId").value(blueprintVersionId.toString()));

        // A published version cannot be mutated by re-materialization.
        materialize(version[1], admin).andExpect(status().isConflict());
    }

    @Test
    void aStudentCannotAuthorAssessments() throws Exception {
        String student = tokenFor(createUser(syntheticEmail("assessment-student"), PASSWORD, "STUDENT"));
        mockMvc.perform(get("/api/v1/blueprints").header(AUTHORIZATION, bearer(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/assessments").header(AUTHORIZATION, bearer(student)))
                .andExpect(status().isForbidden());
    }

    // ----- helpers -----

    private String adminToken() {
        return tokenFor(createUser(syntheticEmail("assessment-admin"), PASSWORD, "ADMIN"));
    }

    private String staffToken() {
        return tokenFor(createUser(syntheticEmail("assessment-staff"), PASSWORD, "STAFF"));
    }

    private String reviewerToken() {
        return tokenFor(createUser(syntheticEmail("assessment-reviewer"), PASSWORD, "REVIEWER"));
    }

    private UUID templateId() {
        return templateRepository.findByCode("SINGLE_CHOICE").orElseThrow().getId();
    }

    private String defaultDeliveryPolicyId() {
        return jdbcTemplate.queryForObject(
                "select id::text from delivery_policy where code = 'DEFAULT'", String.class);
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

    /** Runs the full Content authoring journey to produce one published QuestionVersion. */
    private String publishQuestionVersion(UUID examId, UUID subjectId, UUID languageId) throws Exception {
        String staff = staffToken();
        String reviewer = reviewerToken();
        String admin = adminToken();

        String questionBody = "{\"templateId\":\"" + templateId() + "\",\"examId\":\"" + examId
                + "\",\"subjectId\":\"" + subjectId + "\",\"title\":\"Q\",\"content\":\"What is 2+2?\","
                + "\"marks\":2,\"negativeMarks\":0,\"difficulty\":\"EASY\",\"languageId\":\"" + languageId + "\"}";
        String questionCreated = mockMvc.perform(post("/api/v1/questions").header(AUTHORIZATION, bearer(staff))
                        .contentType(JSON).content(questionBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String questionId = JsonPath.read(questionCreated, "$.data.id");

        String versions = mockMvc.perform(get("/api/v1/questions/" + questionId + "/versions")
                        .header(AUTHORIZATION, bearer(staff)))
                .andReturn().getResponse().getContentAsString();
        String versionId = JsonPath.read(versions, "$.data[0].id");

        addOption(staff, questionId, versionId, "A", 1, true);
        addOption(staff, questionId, versionId, "B", 2, false);

        mockMvc.perform(post("/api/v1/questions/" + questionId + "/versions/" + versionId + "/submit-review")
                .header(AUTHORIZATION, bearer(staff))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/questions/" + questionId + "/versions/" + versionId + "/approve")
                        .header(AUTHORIZATION, bearer(reviewer)).contentType(JSON).content("{\"remarks\":\"ok\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/questions/" + questionId + "/versions/" + versionId + "/publish")
                .header(AUTHORIZATION, bearer(admin))).andExpect(status().isOk());
        return versionId;
    }

    private void addOption(String token, String questionId, String versionId, String key, int order, boolean correct)
            throws Exception {
        mockMvc.perform(post("/api/v1/questions/" + questionId + "/versions/" + versionId + "/options")
                        .header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"optionKey\":\"" + key + "\",\"content\":\"option " + key
                                + "\",\"displayOrder\":" + order + ",\"isCorrect\":" + correct + "}"))
                .andExpect(status().isCreated());
    }

    private UUID publishBlueprintWithSection(String admin, int questionCount) throws Exception {
        String code = "BP-" + UUID.randomUUID();
        String created = mockMvc.perform(post("/api/v1/blueprints").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON).content("{\"code\":\"" + code + "\",\"name\":\"Blueprint\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String blueprintId = JsonPath.read(created, "$.data.id");

        String configuration = "{\"sections\":[{\"name\":\"General\",\"displayOrder\":1,\"questionCount\":"
                + questionCount + ",\"marks\":4,\"durationMinutes\":10}],\"navigation\":{\"allowSectionSwitch\":true},"
                + "\"calculator\":{\"enabled\":false}}";
        String versionCreated = mockMvc.perform(post("/api/v1/blueprints/" + blueprintId + "/versions")
                        .header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"configuration\":" + configuration + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String versionId = JsonPath.read(versionCreated, "$.data.id");
        mockMvc.perform(post("/api/v1/blueprints/" + blueprintId + "/versions/" + versionId + "/publish")
                .header(AUTHORIZATION, bearer(admin))).andExpect(status().isOk());
        return UUID.fromString(versionId);
    }

    /** @return {assessmentId, assessmentVersionId} */
    private String[] createAssessmentVersionWithRule(String admin, UUID blueprintVersionId, UUID examId, int target)
            throws Exception {
        String assessmentId = createAssessment(admin, "AS-" + UUID.randomUUID());
        String body = "{\"blueprintVersionId\":\"" + blueprintVersionId + "\",\"durationMinutes\":60,"
                + "\"engineType\":\"CUSTOM\",\"deliveryPolicyId\":\"" + defaultDeliveryPolicyId() + "\"}";
        String versionCreated = mockMvc.perform(post("/api/v1/assessments/" + assessmentId + "/versions")
                        .header(AUTHORIZATION, bearer(admin)).contentType(JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String versionId = JsonPath.read(versionCreated, "$.data.id");

        String ruleBody = "{\"sectionName\":\"General\",\"targetCount\":" + target + ",\"displayOrder\":1,"
                + "\"ruleConfiguration\":{\"templateCode\":\"SINGLE_CHOICE\",\"examId\":\"" + examId
                + "\",\"difficulty\":[\"EASY\"],\"publishedOnly\":true}}";
        mockMvc.perform(post("/api/v1/assessment-versions/" + versionId + "/rules")
                        .header(AUTHORIZATION, bearer(admin)).contentType(JSON).content(ruleBody))
                .andExpect(status().isCreated());
        return new String[] {assessmentId, versionId};
    }

    private String createAssessment(String admin, String code) throws Exception {
        String created = mockMvc.perform(post("/api/v1/assessments").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON).content("{\"code\":\"" + code + "\",\"name\":\"Assessment\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.data.id");
    }

    private org.springframework.test.web.servlet.ResultActions materialize(String versionId, String admin)
            throws Exception {
        return mockMvc.perform(post("/api/v1/assessment-versions/" + versionId + "/rules/materialize")
                .header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                .content("{\"mode\":\"REPLACE_UNMATERIALIZED\"}"));
    }

    private Set<String> materializedVersions(String versionId, String admin) throws Exception {
        String body = mockMvc.perform(get("/api/v1/assessment-versions/" + versionId + "/questions")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.data[*].questionVersionId");
        return new HashSet<>(ids);
    }
}
