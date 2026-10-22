package com.company.exam.integration.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The assessment list {@code examId} filter ({@code specs/13-P4-Assessment-V1.5.md §64, §95};
 * ADR-0027).
 *
 * <p>An assessment is linked to an exam through the selection-rule provenance: a version's selection
 * rule scopes its question pool to that exam, and that is the scope the platform acts on when it
 * materializes. The filter returns the assessments authored against an exam, and an exam that no
 * assessment is authored against matches nothing.
 */
class AssessmentExamFilterIntegrationTest extends AbstractIdentityIntegrationTest {

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

    @Test
    void theExamIdFilterReturnsTheAssessmentsAuthoredAgainstThatExam() throws Exception {
        String admin = adminToken();
        UUID examId = createExam();
        UUID otherExamId = createExam();
        UUID blueprintVersionId = publishBlueprintWithSection(admin, 1);

        String authored = createAssessmentVersionWithRule(admin, blueprintVersionId, examId)[0];
        String unrelated = createAssessmentVersionWithRule(admin, blueprintVersionId, otherExamId)[0];
        // An assessment with no version at all is never scoped to an exam.
        String noVersions = createAssessment(admin);

        String body = mockMvc.perform(get("/api/v1/assessments").param("examId", examId.toString())
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(body, "$.data[*].id");
        assertThat(ids).contains(authored).doesNotContain(unrelated, noVersions);
    }

    @Test
    void theExamIdFilterReturnsNoMatchesForAnExamNoAssessmentIsAuthoredAgainst() throws Exception {
        String admin = adminToken();
        UUID examId = createExam();
        UUID unrelatedExamId = createExam();
        UUID blueprintVersionId = publishBlueprintWithSection(admin, 1);
        createAssessmentVersionWithRule(admin, blueprintVersionId, examId);

        // An exam that exists but has no assessment: an empty result, not an error.
        mockMvc.perform(get("/api/v1/assessments").param("examId", unrelatedExamId.toString())
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // An exam id that matches nothing at all: also an empty result.
        mockMvc.perform(get("/api/v1/assessments").param("examId", UUID.randomUUID().toString())
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void theExamIdFilterComposesWithTheOtherFilters() throws Exception {
        String admin = adminToken();
        UUID examId = createExam();
        UUID blueprintVersionId = publishBlueprintWithSection(admin, 1);
        String authored = createAssessmentVersionWithRule(admin, blueprintVersionId, examId)[0];

        // The exam filter alone matches.
        mockMvc.perform(get("/api/v1/assessments").param("examId", examId.toString())
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(authored));

        // Combined with a status filter that excludes it, the result is empty.
        mockMvc.perform(get("/api/v1/assessments").param("examId", examId.toString())
                        .param("status", "INACTIVE")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // The other filters are unaffected when no exam filter is supplied: the same assessment is
        // still visible. (The absolute count is not asserted: the database is shared across the
        // suite, so other assessments exist.)
        String unfiltered = mockMvc.perform(get("/api/v1/assessments").param("status", "ACTIVE")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(unfiltered, "$.data[*].id")).contains(authored);
    }

    // ----- helpers -----

    private String adminToken() {
        return tokenFor(createUser(syntheticEmail("assessment-filter-admin"), PASSWORD, "ADMIN"));
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

    /**
     * Creates an assessment and one version whose selection rule is scoped to the given exam.
     *
     * @return {assessmentId, assessmentVersionId}
     */
    private String[] createAssessmentVersionWithRule(String admin, UUID blueprintVersionId, UUID examId)
            throws Exception {
        String assessmentId = createAssessment(admin);
        String body = "{\"blueprintVersionId\":\"" + blueprintVersionId + "\",\"durationMinutes\":60,"
                + "\"engineType\":\"CUSTOM\",\"deliveryPolicyId\":\"" + defaultDeliveryPolicyId() + "\"}";
        String versionCreated = mockMvc.perform(post("/api/v1/assessments/" + assessmentId + "/versions")
                        .header(AUTHORIZATION, bearer(admin)).contentType(JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String versionId = JsonPath.read(versionCreated, "$.data.id");

        String ruleBody = "{\"sectionName\":\"General\",\"targetCount\":1,\"displayOrder\":1,"
                + "\"ruleConfiguration\":{\"templateCode\":\"SINGLE_CHOICE\",\"examId\":\"" + examId
                + "\",\"difficulty\":[\"EASY\"],\"publishedOnly\":true}}";
        mockMvc.perform(post("/api/v1/assessment-versions/" + versionId + "/rules")
                .header(AUTHORIZATION, bearer(admin)).contentType(JSON).content(ruleBody))
                .andExpect(status().isCreated());
        return new String[] {assessmentId, versionId};
    }

    private String createAssessment(String admin) throws Exception {
        String created = mockMvc.perform(post("/api/v1/assessments").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON).content("{\"code\":\"AS-" + UUID.randomUUID() + "\",\"name\":\"Assessment\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.data.id");
    }
}
