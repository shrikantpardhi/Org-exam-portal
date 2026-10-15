package com.company.exam.integration.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.analytics.dto.AnalyticsDtos;
import com.company.exam.analytics.repository.QuestionAnalyticsRepository;
import com.company.exam.analytics.repository.StudentAnalyticsRepository;
import com.company.exam.analytics.service.AnalyticsQueryService;
import com.company.exam.analytics.service.AnalyticsRefreshService;
import com.company.exam.identity.entity.User;
import com.company.exam.integration.evaluation.AbstractEvaluationIntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * P8 analytics correctness, rebuild determinism, transactional isolation, cache fallback, and
 * security ({@code specs/17-P8-Analytics-V1.5.md §10, §35, §62, §95, §107, §123, §153, §155}).
 */
class AnalyticsIntegrationTest extends AbstractEvaluationIntegrationTest {

    @Autowired
    private AnalyticsRefreshService refreshService;
    @Autowired
    private AnalyticsQueryService queryService;
    @Autowired
    private StudentAnalyticsRepository studentAnalyticsRepository;
    @Autowired
    private QuestionAnalyticsRepository questionAnalyticsRepository;

    @Test
    void rebuildIsDeterministicAndDoesNotModifyTransactionalTables() throws Exception {
        String versionId = publishAssessmentVersion(2);
        String scheduleId = createSchedule(versionId, "EXAM");
        User studentA = newStudent("p8-det-a");
        User studentB = newStudent("p8-det-b");
        prepareAttempts(scheduleId);
        String keyId = publishKeyFromSchedule(scheduleId, studentA, versionId);
        runStudent(scheduleId, studentA, versionId, keyId, true);
        runStudent(scheduleId, studentB, versionId, keyId, false);

        Map<String, Long> transactionalBefore = transactionalCounts();

        int firstUpserts = refreshService.rebuildAll();
        String signatureFirst = signature();
        int secondUpserts = refreshService.rebuildAll();
        String signatureSecond = signature();

        assertThat(firstUpserts).isPositive();
        assertThat(secondUpserts).isEqualTo(firstUpserts);
        assertThat(signatureSecond).as("rebuild is deterministic").isEqualTo(signatureFirst);
        assertThat(transactionalCounts()).as("analytics never modifies transactional tables")
                .isEqualTo(transactionalBefore);

        // The student grain reflects exactly the two qualifying attempts.
        assertThat(studentAnalyticsRepository.findByUserId(studentA.getId())).isPresent();
        assertThat(studentAnalyticsRepository.findByUserId(studentA.getId()).orElseThrow()
                .getAssessmentsAttempted()).isEqualTo(1);
        assertThat(queryService.organization().totalAttempts()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void difficultyIndexUsesTheFrozenFormulaEndToEnd() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User correctStudent = newStudent("p8-diff-correct");
        User wrongStudent = newStudent("p8-diff-wrong");
        prepareAttempts(scheduleId);
        String keyId = publishKeyFromSchedule(scheduleId, correctStudent, versionId);
        runStudent(scheduleId, correctStudent, versionId, keyId, true);
        runStudent(scheduleId, wrongStudent, versionId, keyId, false);

        refreshService.rebuildAll();

        UUID questionId = questionIdOfVersion(versionId);
        var row = questionAnalyticsRepository.findByQuestionId(questionId).orElseThrow();
        assertThat(row.getTotalAttempts()).isEqualTo(2);
        assertThat(row.getCorrectAttempts()).isEqualTo(1);
        assertThat(row.getIncorrectAttempts()).isEqualTo(1);
        // difficulty_index = 1 - (1 / 2) = 0.5
        assertThat(row.getDifficultyIndex()).isEqualByComparingTo("0.5000");
    }

    @Test
    void analyticsReadsFallBackToPostgreSqlWhenTheCacheIsUnavailable() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p8-cache");
        prepareAttempts(scheduleId);
        String keyId = publishKeyFromSchedule(scheduleId, student, versionId);
        runStudent(scheduleId, student, versionId, keyId, true);
        refreshService.rebuildAll();

        // The test profile points Redis at an unreachable port, so every read is a cache miss that
        // must fall back to PostgreSQL and still return the correct value (§62, §2015).
        AnalyticsDtos.StudentAnalyticsResponse first = queryService.student(student.getId());
        AnalyticsDtos.StudentAnalyticsResponse second = queryService.student(student.getId());
        assertThat(first.assessmentsAttempted()).isEqualTo(1);
        assertThat(first.averageScore()).isNotNull();
        assertThat(second.assessmentsAttempted()).isEqualTo(first.assessmentsAttempted());
        assertThat(second.averageScore()).isEqualByComparingTo(first.averageScore());

        UUID questionId = questionIdOfVersion(versionId);
        assertThat(queryService.question(questionId).totalAttempts()).isEqualTo(1);
        assertThat(queryService.assessment(assessmentIdOfVersion(versionId)).totalAttempts()).isEqualTo(1);
        assertThat(queryService.organization().totalStudents()).isPositive();
    }

    @Test
    void studentsCannotReadStaffAnalyticsAndSeeOnlyTheirOwn() throws Exception {
        String versionId = publishAssessmentVersion(1);
        String scheduleId = createSchedule(versionId, "EXAM");
        User student = newStudent("p8-security");
        String studentToken = tokenFor(student);
        prepareAttempts(scheduleId);
        String keyId = publishKeyFromSchedule(scheduleId, student, versionId);
        runStudent(scheduleId, student, versionId, keyId, true);
        refreshService.rebuildAll();
        UUID assessmentId = assessmentIdOfVersion(versionId);

        mockMvc.perform(get("/api/v1/analytics/student").header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.assessmentsAttempted").value(1));
        mockMvc.perform(get("/api/v1/analytics/assessments/" + assessmentId)
                        .header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/analytics/organization").header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/analytics/assessments/" + assessmentId)
                        .header(AUTHORIZATION, bearer(staffToken())))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ helpers

    private String publishKeyFromSchedule(String scheduleId, User student, String versionId) throws Exception {
        UUID attemptId = attemptIdFor(UUID.fromString(scheduleId), student.getId());
        return publishAnswerKeyWithOptionA(attemptId, versionId);
    }

    private void runStudent(String scheduleId, User student, String versionId, String keyId, boolean correct)
            throws Exception {
        String token = tokenFor(student);
        UUID attemptId = startAttempt(scheduleId, token);
        for (UUID questionVersionId : snapshotQuestionVersionIds(attemptId)) {
            answer(attemptId, questionVersionId, correct ? "A" : "B", token);
        }
        submit(attemptId, token);
        String resultId = evaluate(attemptId, keyId);
        publishResult(resultId);
    }

    private UUID questionIdOfVersion(String assessmentVersionId) {
        return jdbcTemplate.queryForObject(
                "select q.id from question q join question_version v on v.question_id = q.id "
                        + "join assessment_question aq on aq.question_version_id = v.id "
                        + "where aq.assessment_version_id = ? limit 1",
                UUID.class, UUID.fromString(assessmentVersionId));
    }

    private UUID assessmentIdOfVersion(String assessmentVersionId) {
        return jdbcTemplate.queryForObject("select assessment_id from assessment_version where id = ?",
                UUID.class, UUID.fromString(assessmentVersionId));
    }

    private Map<String, Long> transactionalCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : List.of("user", "assessment", "assessment_version", "assessment_question",
                "student_attempt", "attempt_question", "attempt_question_option", "student_response",
                "evaluation", "result", "answer_key", "answer_key_detail")) {
            String quoted = "user".equals(table) ? "\"user\"" : table;
            counts.put(table, jdbcTemplate.queryForObject("select count(*) from " + quoted, Long.class));
        }
        return counts;
    }

    private String signature() {
        return rows("select user_id, assessments_attempted, average_score, accuracy, average_time "
                + "from student_analytics order by user_id")
                + "|" + rows("select assessment_id, total_attempts, average_score, highest_score, lowest_score "
                        + "from assessment_analytics order by assessment_id")
                + "|" + rows("select question_id, total_attempts, correct_attempts, incorrect_attempts, "
                        + "average_time, difficulty_index from question_analytics order by question_id")
                + "|" + rows("select topic_id, total_attempts, average_score, accuracy "
                        + "from topic_analytics order by topic_id")
                + "|" + rows("select organization_id, total_students, total_assessments, total_attempts, "
                        + "active_users from organization_analytics order by organization_id");
    }

    private String rows(String sql) {
        return jdbcTemplate.queryForList(sql).toString();
    }
}
