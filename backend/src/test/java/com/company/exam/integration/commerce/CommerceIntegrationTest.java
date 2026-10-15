package com.company.exam.integration.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.exam.commerce.api.EntitlementResolver;
import com.company.exam.commerce.gateway.TestPaymentGateway;
import com.company.exam.identity.entity.User;
import com.company.exam.integration.evaluation.AbstractEvaluationIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * P9 commerce end-to-end: server-side pricing, payment initiation, callback idempotency and amount
 * verification, AccessGrant creation and revocation, and student isolation
 * ({@code specs/18-P9-Commerce-V1.5.md §11.1, §19-§26, §31, §67-§71, §94-§98}).
 */
class CommerceIntegrationTest extends AbstractEvaluationIntegrationTest {

    @Autowired
    private TestPaymentGateway testGateway;
    @Autowired
    private EntitlementResolver entitlementResolver;

    // ------------------------------------------------------------------ pricing

    @Test
    void purchaseAppliesTheServerCalculatedCouponDiscount() throws Exception {
        Fixture fixture = fixture();
        User student = newStudent("p9-price");

        String priced = mockMvc.perform(post("/api/v1/purchases")
                        .header(AUTHORIZATION, bearer(tokenFor(student))).contentType(JSON)
                        .content("{\"subscriptionPlanId\":\"" + fixture.planId() + "\",\"couponCode\":\""
                                + fixture.couponCode() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        // 1000.00 list price - 100.00 fixed-amount coupon = 900.00, computed on the server
        assertThat(new BigDecimal(JsonPath.read(priced, "$.data.amount").toString()))
                .isEqualByComparingTo("900.00");

        String full = mockMvc.perform(post("/api/v1/purchases")
                        .header(AUTHORIZATION, bearer(tokenFor(student))).contentType(JSON)
                        .content("{\"subscriptionPlanId\":\"" + fixture.planId() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(new BigDecimal(JsonPath.read(full, "$.data.amount").toString()))
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void purchaseRejectsAClientSuppliedAmount() throws Exception {
        Fixture fixture = fixture();
        User student = newStudent("p9-tamper");
        // The request has no amount field; an attempted amount override is rejected outright.
        mockMvc.perform(post("/api/v1/purchases")
                        .header(AUTHORIZATION, bearer(tokenFor(student))).contentType(JSON)
                        .content("{\"subscriptionPlanId\":\"" + fixture.planId()
                                + "\",\"amount\":1.00,\"discount\":900.00}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ callback

    @Test
    void duplicateCallbackCreatesExactlyOneGrant() throws Exception {
        Fixture fixture = fixture();
        User student = newStudent("p9-callback");
        String purchaseId = createPurchase(student, fixture, null);
        BigDecimal amount = new BigDecimal("1000.00");
        String reference = initiatePayment(student, purchaseId);

        String first = callback(reference, "CAPTURED", amount, testGateway.signatureFor(reference, "CAPTURED", amount, "INR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessGranted").value(true))
                .andExpect(jsonPath("$.data.duplicate").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.read(first, "$.data.paymentStatus").toString()).isEqualTo("SUCCESS");
        assertThat(activeGrantCount(student.getId(), fixture.packageId())).isEqualTo(1);
        assertThat(entitlementResolver.canAccessAssessment(student.getId(), fixture.assessmentId())).isTrue();

        // A repeated success callback is idempotent and creates no second grant (§21, §70).
        callback(reference, "CAPTURED", amount, testGateway.signatureFor(reference, "CAPTURED", amount, "INR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicate").value(true));
        assertThat(activeGrantCount(student.getId(), fixture.packageId())).isEqualTo(1);
    }

    @Test
    void callbackWithATamperedAmountIsRejectedAndGrantsNothing() throws Exception {
        Fixture fixture = fixture();
        User student = newStudent("p9-amount");
        String purchaseId = createPurchase(student, fixture, null);
        String reference = initiatePayment(student, purchaseId);

        // The signature is valid for the attacker's amount, but it does not match the purchase amount.
        BigDecimal tampered = new BigDecimal("1.00");
        callback(reference, "CAPTURED", tampered,
                testGateway.signatureFor(reference, "CAPTURED", tampered, "INR"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("AMOUNT_MISMATCH"));
        assertThat(activeGrantCount(student.getId(), fixture.packageId())).isZero();
        assertThat(jdbcTemplate.queryForObject("select payment_status from purchase where id = ?", String.class,
                UUID.fromString(purchaseId))).isEqualTo("PENDING");
    }

    @Test
    void callbackWithAnInvalidSignatureIsRejected() throws Exception {
        Fixture fixture = fixture();
        User student = newStudent("p9-spoof");
        String purchaseId = createPurchase(student, fixture, null);
        String reference = initiatePayment(student, purchaseId);

        callback(reference, "CAPTURED", new BigDecimal("1000.00"), "deadbeef")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("CALLBACK_NOT_AUTHENTIC"));
        assertThat(activeGrantCount(student.getId(), fixture.packageId())).isZero();
    }

    @Test
    void failedPaymentCreatesNoGrant() throws Exception {
        Fixture fixture = fixture();
        User student = newStudent("p9-failed");
        String purchaseId = createPurchase(student, fixture, null);
        String reference = initiatePayment(student, purchaseId);
        BigDecimal amount = new BigDecimal("1000.00");

        callback(reference, "FAILED", amount, testGateway.signatureFor(reference, "FAILED", amount, "INR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessGranted").value(false))
                .andExpect(jsonPath("$.data.paymentStatus").value("FAILED"));
        assertThat(activeGrantCount(student.getId(), fixture.packageId())).isZero();
    }

    // ------------------------------------------------------------------ refund

    @Test
    void refundRevokesTheGrantAndIsIdempotent() throws Exception {
        Fixture fixture = fixture();
        User student = newStudent("p9-refund");
        String purchaseId = createPurchase(student, fixture, null);
        String reference = initiatePayment(student, purchaseId);
        BigDecimal amount = new BigDecimal("1000.00");
        callback(reference, "CAPTURED", amount, testGateway.signatureFor(reference, "CAPTURED", amount, "INR"))
                .andExpect(status().isOk());
        assertThat(activeGrantCount(student.getId(), fixture.packageId())).isEqualTo(1);

        mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/refund")
                        .header(AUTHORIZATION, bearer(financeToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));

        // The grant is INACTIVE and its end date is set to the refund time (§20).
        assertThat(activeGrantCount(student.getId(), fixture.packageId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from access_grant where user_id = ? and access_package_id = ? "
                        + "and status = 'INACTIVE' and end_date is not null",
                Long.class, student.getId(), fixture.packageId())).isEqualTo(1L);
        assertThat(entitlementResolver.canAccessAssessment(student.getId(), fixture.assessmentId())).isFalse();

        // A repeated refund is idempotent.
        mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/refund")
                        .header(AUTHORIZATION, bearer(financeToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refunded").value(true));
    }

    // ------------------------------------------------------------------ isolation

    @Test
    void studentsCannotReadOrActOnAnotherStudentsPurchase() throws Exception {
        Fixture fixture = fixture();
        User owner = newStudent("p9-owner");
        User other = newStudent("p9-other");
        String purchaseId = createPurchase(owner, fixture, null);
        String ownerToken = tokenFor(owner);
        String otherToken = tokenFor(other);

        mockMvc.perform(get("/api/v1/purchases/" + purchaseId).header(AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/payment")
                        .header(AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/purchases/" + purchaseId + "/payment")
                        .header(AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        // The owner sees it; a student cannot refund it.
        mockMvc.perform(get("/api/v1/purchases/" + purchaseId).header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/refund")
                        .header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isForbidden());

        // Only the owner's own grants are listed.
        String grants = mockMvc.perform(get("/api/v1/access-grants").header(AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(grants).doesNotContain(owner.getId().toString());
    }

    // ------------------------------------------------------------------ helpers

    private record Fixture(UUID assessmentId, UUID packageId, UUID planId, String couponCode) {
    }

    private Fixture fixture() throws Exception {
        String versionId = publishAssessmentVersion(1);
        UUID assessmentId = jdbcTemplate.queryForObject("select assessment_id from assessment_version where id = ?",
                UUID.class, UUID.fromString(versionId));
        UUID collectionId = UUID.randomUUID();
        jdbcTemplate.update("insert into assessment_collection (id, code, name, status, created_on) "
                + "values (?, ?, ?, 'ACTIVE', now())", collectionId, "P9-COL-" + collectionId, "P9 Collection");
        jdbcTemplate.update("insert into collection_assessment (id, collection_id, assessment_id, status, created_on) "
                + "values (?, ?, ?, 'ACTIVE', now())", UUID.randomUUID(), collectionId, assessmentId);

        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        String admin = adminToken();
        String packageId = postJson("/api/v1/access-packages",
                "{\"code\":\"P9-PKG-" + suffix + "\",\"name\":\"P9 Package\"}", admin);
        mockMvc.perform(post("/api/v1/access-packages/" + packageId + "/collections")
                        .header(AUTHORIZATION, bearer(admin)).contentType(JSON)
                        .content("{\"collectionId\":\"" + collectionId + "\"}"))
                .andExpect(status().isCreated());
        String planId = postJson("/api/v1/subscription-plans",
                "{\"accessPackageId\":\"" + packageId + "\",\"code\":\"P9-PLAN-" + suffix
                        + "\",\"name\":\"P9 30 days\",\"durationDays\":30,\"price\":1000.00}", admin);
        String couponCode = "P9-WELCOME-" + suffix;
        postJson("/api/v1/coupons", "{\"code\":\"" + couponCode
                + "\",\"name\":\"Welcome\",\"discountValue\":100.00}", admin);
        return new Fixture(assessmentId, UUID.fromString(packageId), UUID.fromString(planId), couponCode);
    }

    private String postJson(String url, String body, String token) throws Exception {
        String response = mockMvc.perform(post(url).header(AUTHORIZATION, bearer(token)).contentType(JSON)
                        .content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private String createPurchase(User student, Fixture fixture, String couponCode) throws Exception {
        String body = couponCode == null
                ? "{\"subscriptionPlanId\":\"" + fixture.planId() + "\"}"
                : "{\"subscriptionPlanId\":\"" + fixture.planId() + "\",\"couponCode\":\"" + couponCode + "\"}";
        String response = mockMvc.perform(post("/api/v1/purchases")
                        .header(AUTHORIZATION, bearer(tokenFor(student))).contentType(JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private String initiatePayment(User student, String purchaseId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/payment")
                        .header(AUTHORIZATION, bearer(tokenFor(student))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.paymentReference");
    }

    private org.springframework.test.web.servlet.ResultActions callback(String reference, String status,
            BigDecimal amount, String signature) throws Exception {
        String body = "{\"paymentReference\":\"" + reference + "\",\"providerStatus\":\"" + status
                + "\",\"amount\":" + amount.toPlainString() + ",\"currency\":\"INR\",\"signature\":\""
                + signature + "\"}";
        // No Authorization header: the callback is authenticated by the provider signature only (§69).
        return mockMvc.perform(post("/api/v1/payments/callback").contentType(JSON).content(body));
    }

    private long activeGrantCount(UUID userId, UUID packageId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from access_grant where user_id = ? and access_package_id = ? and status = 'ACTIVE'",
                Long.class, userId, packageId);
        return count == null ? 0 : count;
    }

    private String financeToken() {
        return tokenFor(createUser(syntheticEmail("p9-finance"), PASSWORD, "FINANCE"));
    }
}
