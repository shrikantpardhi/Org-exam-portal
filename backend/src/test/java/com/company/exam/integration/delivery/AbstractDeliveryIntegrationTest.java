package com.company.exam.integration.delivery;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.academic.entity.Exam;
import com.company.exam.academic.entity.Language;
import com.company.exam.academic.entity.Subject;
import com.company.exam.academic.repository.ExamRepository;
import com.company.exam.academic.repository.LanguageRepository;
import com.company.exam.academic.repository.SubjectRepository;
import com.company.exam.common.enums.Status;
import com.company.exam.content.repository.QuestionTemplateRepository;
import com.company.exam.delivery.application.SnapshotPreparationService;
import com.company.exam.identity.entity.User;
import com.company.exam.integration.identity.AbstractIdentityIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared setup for P5 Delivery integration tests. It publishes one complete assessment version
 * (question content, blueprint, materialized questions, publication) and opens a schedule, mirroring
 * the frozen P3/P4 authoring flow.
 */
public abstract class AbstractDeliveryIntegrationTest extends AbstractIdentityIntegrationTest {

    protected static final String PASSWORD = "Correct-Horse-Battery-9";
    protected static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    protected ExamRepository examRepository;
    @Autowired
    protected SubjectRepository subjectRepository;
    @Autowired
    protected LanguageRepository languageRepository;
    @Autowired
    protected QuestionTemplateRepository templateRepository;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected SnapshotPreparationService snapshotPreparationService;

    protected String adminToken() {
        return tokenFor(createUser(syntheticEmail("delivery-admin"), PASSWORD, "ADMIN"));
    }

    protected String staffToken() {
        return tokenFor(createUser(syntheticEmail("delivery-staff"), PASSWORD, "STAFF"));
    }

    protected String reviewerToken() {
        return tokenFor(createUser(syntheticEmail("delivery-reviewer"), PASSWORD, "REVIEWER"));
    }

    protected User newStudent(String prefix) {
        return createUser(syntheticEmail(prefix), PASSWORD, "STUDENT");
    }

    protected UUID templateId() {
        return templateRepository.findByCode("SINGLE_CHOICE").orElseThrow().getId();
    }

    protected String defaultDeliveryPolicyId() {
        return jdbcTemplate.queryForObject(
                "select id::text from delivery_policy where code = 'DEFAULT'", String.class);
    }

    /** Publishes a complete assessment version with {@code questionCount} published questions. */
    protected String publishAssessmentVersion(int questionCount) throws Exception {
        String admin = adminToken();
        UUID examId = createExam();
        UUID subjectId = createSubject(examId);
        UUID languageId = createLanguage();
        for (int i = 0; i < questionCount; i++) {
            publishQuestionVersion(examId, subjectId, languageId);
        }
        UUID blueprintVersionId = publishBlueprintWithSection(admin, questionCount);
        String versionId = createAssessmentVersionWithRule(admin, blueprintVersionId, examId, questionCount);
        materialize(versionId, admin);
        mockMvc.perform(post("/api/v1/assessment-versions/" + versionId + "/publish")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        return versionId;
    }

    /** Creates an open schedule for the published version. */
    protected String createSchedule(String assessmentVersionId, String mode) throws Exception {
        String admin = adminToken();
        String start = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        String end = Instant.now().plus(3, ChronoUnit.HOURS).toString();
        String body = "{\"assessmentVersionId\":\"" + assessmentVersionId + "\",\"mode\":\"" + mode
                + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
        String created = mockMvc.perform(post("/api/v1/assessment-schedules")
                        .header(AUTHORIZATION, bearer(admin)).contentType(JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.data.id");
    }

    /** Triggers preparation through the frozen endpoint and then materializes synchronously. */
    protected void prepareAttempts(String scheduleId) throws Exception {
        String admin = adminToken();
        mockMvc.perform(post("/api/v1/assessment-schedules/" + scheduleId + "/prepare-attempts")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isAccepted());
        snapshotPreparationService.preparePendingBatch(500);
    }

    protected long countAttemptQuestions(UUID attemptId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from attempt_question where attempt_id = ?", Long.class, attemptId);
        return count == null ? 0 : count;
    }

    protected long countSessions(UUID attemptId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from assessment_session where attempt_id = ?", Long.class, attemptId);
        return count == null ? 0 : count;
    }

    protected UUID attemptIdFor(UUID scheduleId, UUID userId) {
        return jdbcTemplate.queryForObject(
                "select id from student_attempt where assessment_schedule_id = ? and user_id = ?",
                UUID.class, scheduleId, userId);
    }

    // ----- content authoring helpers (mirror the P3/P4 flow) -----

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

        String versions = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/questions/" + questionId + "/versions")
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

    private String createAssessmentVersionWithRule(String admin, UUID blueprintVersionId, UUID examId, int target)
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
        return versionId;
    }

    private String createAssessment(String admin, String code) throws Exception {
        String created = mockMvc.perform(post("/api/v1/assessments").header(AUTHORIZATION, bearer(admin))
                        .contentType(JSON).content("{\"code\":\"" + code + "\",\"name\":\"Assessment\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.data.id");
    }

    private void materialize(String versionId, String admin) throws Exception {
        mockMvc.perform(post("/api/v1/assessment-versions/" + versionId + "/rules/materialize")
                        .header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"mode\":\"REPLACE_UNMATERIALIZED\"}"))
                .andExpect(status().isOk());
    }
}
