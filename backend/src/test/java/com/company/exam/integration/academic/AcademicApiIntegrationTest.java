package com.company.exam.integration.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.integration.identity.AbstractIdentityIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * End-to-end Academic API behaviour ({@code specs/11-P2-Academic-V1.5.md §107, §108, §111, §112}).
 *
 * <p>Proves parent-child integrity, scoped code uniqueness, and non-cascading archive semantics.
 */
class AcademicApiIntegrationTest extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private String adminToken() {
        return tokenFor(createUser(syntheticEmail("academic-admin"), PASSWORD, "ADMIN"));
    }

    @Test
    void aCompleteHierarchyCanBeCreatedAndRead() throws Exception {
        String token = adminToken();
        String examId = createExam(token);
        String subjectId = createSubject(token, examId, "MATH");
        String chapterId = createChapter(token, subjectId, "ALG");
        String topicId = createTopic(token, chapterId, "QUAD");
        String subTopicId = createSubTopic(token, topicId, "ROOTS");

        mockMvc.perform(get("/api/v1/sub-topics/" + subTopicId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topicId").value(topicId));

        // Language and Tag masters.
        mockMvc.perform(post("/api/v1/languages").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"code\":\"ENG-" + suffix() + "\",\"name\":\"English\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tags").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"code\":\"ALGEBRA-" + suffix() + "\",\"name\":\"Algebra\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void creatingASubjectUnderAMissingExamIsNotFound() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/v1/subjects").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"examId\":\"" + UUID.randomUUID() + "\",\"code\":\"MATH\",\"name\":\"Math\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingAChildUnderAnArchivedParentIsRejected() throws Exception {
        String token = adminToken();
        String examId = createExam(token);
        String subjectId = createSubject(token, examId, "MATH");

        mockMvc.perform(delete("/api/v1/subjects/" + subjectId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/chapters").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"subjectId\":\"" + subjectId + "\",\"code\":\"ALG\",\"name\":\"Algebra\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PARENT_NOT_ELIGIBLE"));
    }

    @Test
    void creatingAChildUnderAnInactiveParentIsRejected() throws Exception {
        String token = adminToken();
        String examId = createExam(token);
        String subjectId = createSubject(token, examId, "MATH");

        mockMvc.perform(put("/api/v1/subjects/" + subjectId).header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/chapters").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"subjectId\":\"" + subjectId + "\",\"code\":\"ALG\",\"name\":\"Algebra\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PARENT_NOT_ELIGIBLE"));
    }

    @Test
    void duplicateExamCodeIsConflict() throws Exception {
        String token = adminToken();
        String code = "EX-" + suffix();

        mockMvc.perform(post("/api/v1/exams").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Exam\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/exams").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Exam\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void subjectCodeIsScopedToItsExam() throws Exception {
        String token = adminToken();
        String examA = createExam(token);
        String examB = createExam(token);
        String code = "MATH-" + suffix();

        createSubject(token, examA, code);

        // Same code under a different exam is allowed.
        createSubject(token, examB, code);

        // Duplicate within the same exam is rejected.
        mockMvc.perform(post("/api/v1/subjects").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content(subjectBody(examA, code)))
                .andExpect(status().isConflict());
    }

    @Test
    void archivingAParentWithActiveDescendantsIsRejectedAndDoesNotCascade() throws Exception {
        String token = adminToken();
        String examId = createExam(token);
        String subjectId = createSubject(token, examId, "MATH");

        mockMvc.perform(delete("/api/v1/exams/" + examId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ACADEMIC_DESCENDANTS_EXIST"));

        // The exam is untouched and the subject remains ACTIVE: nothing cascaded.
        mockMvc.perform(get("/api/v1/exams/" + examId).header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(get("/api/v1/subjects/" + subjectId).header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void aChainCanBeArchivedBottomUpAndALeafArchivesDirectly() throws Exception {
        String token = adminToken();
        String examId = createExam(token);
        String subjectId = createSubject(token, examId, "MATH");
        String chapterId = createChapter(token, subjectId, "ALG");
        String topicId = createTopic(token, chapterId, "QUAD");
        String subTopicId = createSubTopic(token, topicId, "ROOTS");

        mockMvc.perform(delete("/api/v1/sub-topics/" + subTopicId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/topics/" + topicId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/chapters/" + chapterId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/subjects/" + subjectId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/exams/" + examId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/exams/" + examId).header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    @Test
    void listingSubjectsRequiresTheExamFilter() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/v1/subjects").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void childListsReturnOnlyDirectChildrenOfTheRequestedParent() throws Exception {
        String token = adminToken();
        String examA = createExam(token);
        String examB = createExam(token);
        String subjectOfA = createSubject(token, examA, "MATH-A-" + suffix());
        createSubject(token, examB, "MATH-B-" + suffix());
        createChapter(token, subjectOfA, "ALG");

        mockMvc.perform(get("/api/v1/subjects?examId=" + examA).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].examId").value(examA));

        // The chapter listing returns only chapters of the requested subject (lazy, one level).
        mockMvc.perform(get("/api/v1/chapters?subjectId=" + subjectOfA).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].subjectId").value(subjectOfA));
    }

    @Test
    void unsupportedSortFieldIsRejected() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/v1/exams?sort=description,desc").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedUuidIsRejected() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/v1/exams/not-a-uuid").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aStudentCannotReadOrMutateAcademicMasters() throws Exception {
        String token = tokenFor(createUser(syntheticEmail("academic-student"), PASSWORD, "STUDENT"));

        mockMvc.perform(get("/api/v1/exams").header(AUTHORIZATION, bearer(token))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/exams").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"code\":\"X-" + suffix() + "\",\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listIsPaginated() throws Exception {
        String token = adminToken();
        createExam(token);

        mockMvc.perform(get("/api/v1/exams?page=0&size=5").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(5));
    }

    // ----- helpers -----

    private String createExam(String token) throws Exception {
        String code = "EX-" + suffix();
        String body = mockMvc.perform(post("/api/v1/exams").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Exam " + code + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String createSubject(String token, String examId, String code) throws Exception {
        String body = mockMvc.perform(post("/api/v1/subjects").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content(subjectBody(examId, code)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String createChapter(String token, String subjectId, String code) throws Exception {
        String body = mockMvc.perform(post("/api/v1/chapters").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"subjectId\":\"" + subjectId + "\",\"code\":\"" + code + "-" + suffix()
                                + "\",\"name\":\"Chapter\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String createTopic(String token, String chapterId, String code) throws Exception {
        String body = mockMvc.perform(post("/api/v1/topics").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"chapterId\":\"" + chapterId + "\",\"code\":\"" + code + "-" + suffix()
                                + "\",\"name\":\"Topic\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String createSubTopic(String token, String topicId, String code) throws Exception {
        String body = mockMvc.perform(post("/api/v1/sub-topics").header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content("{\"topicId\":\"" + topicId + "\",\"code\":\"" + code + "-" + suffix()
                                + "\",\"name\":\"SubTopic\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String subjectBody(String examId, String code) {
        return "{\"examId\":\"" + examId + "\",\"code\":\"" + code + "\",\"name\":\"Subject " + code + "\"}";
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
