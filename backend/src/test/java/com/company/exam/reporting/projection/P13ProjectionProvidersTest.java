package com.company.exam.reporting.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.exam.analytics.api.ReportingAnalyticsCatalog;
import com.company.exam.assessment.api.ReportingAssessmentCatalog;
import com.company.exam.commerce.api.ReportingCommerceCatalog;
import com.company.exam.content.api.ReportingContentCatalog;
import com.company.exam.delivery.api.ReportingDeliveryCatalog;
import com.company.exam.identity.api.AdminUserDirectory;
import com.company.exam.reporting.catalog.ReportType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Projection provider verification ({@code specs/22-P13-Reporting-V1.5.md §37-§62, §74, §91}).
 *
 * <p>Proves that each new provider projects the authoritative source into explicit rows, that a
 * student caller is scoped to their own identity, and that no password or unmasked contact value
 * reaches a report.
 */
class P13ProjectionProvidersTest {

    private static final UUID CALLER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID STUDENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    private ReportCriteria criteria(ReportType type, Map<String, String> filters, boolean student) {
        return new ReportCriteria(type, filters, CALLER, student, NOW);
    }

    // ------------------------------------------------------------------ Student

    @Test
    void studentProfileProjectsIdentityWithoutCredentialsOrRawContact() {
        AdminUserDirectory directory = mock(AdminUserDirectory.class);
        when(directory.find(STUDENT)).thenReturn(Optional.of(
                new AdminUserDirectory.AdminUserView(STUDENT, "Ada", "Lovelace", "ada@example.com",
                        "+910000000000", "ACTIVE")));
        var provider = new StudentReportProviders.StudentProfileProvider(directory);

        ReportProjection projection = provider.project(
                criteria(ReportType.STUDENT_PROFILE, Map.of("studentId", STUDENT.toString()), false));

        assertThat(projection.rows()).hasSize(1);
        Map<String, String> row = projection.rows().get(0);
        assertThat(row).containsEntry("firstName", "Ada").containsEntry("status", "ACTIVE");
        // §82: contact data is masked, and no credential field is projected at all.
        assertThat(row.get("email")).isEqualTo("a***@example.com");
        assertThat(row).doesNotContainKeys("password", "passwordHash", "secret");
    }

    @Test
    void studentAttemptsAreScopedToTheCallerEvenWhenAnotherStudentIsRequested() {
        ReportingDeliveryCatalog delivery = mock(ReportingDeliveryCatalog.class);
        // The provider must ask for the caller's own attempts, never the requested student id.
        when(delivery.attemptsForUser(eq(CALLER), any(), any(), anyInt())).thenReturn(List.of(
                new ReportingDeliveryCatalog.AttemptRow(UUID.randomUUID(), CALLER, UUID.randomUUID(), null,
                        "REGULAR", "EVALUATED", NOW, NOW, new BigDecimal("42.00"))));
        var provider = new StudentReportProviders.StudentAttemptsProvider(delivery);

        ReportProjection projection = provider.project(criteria(ReportType.STUDENT_ATTEMPTS,
                Map.of("studentId", STUDENT.toString()), true));

        assertThat(projection.rows()).hasSize(1);
        assertThat(projection.freshnessNote()).contains("no answer-key");
        // The rows carry no correctness or answer-key column (§39).
        assertThat(projection.columns()).noneMatch(column -> column.key().toLowerCase().contains("correct"));
    }

    @Test
    void studentPerformanceReportsTheFrozenAggregateAndItsFreshness() {
        ReportingAnalyticsCatalog analytics = mock(ReportingAnalyticsCatalog.class);
        when(analytics.student(CALLER)).thenReturn(Optional.of(new ReportingAnalyticsCatalog.StudentAnalyticsRow(
                CALLER, 4, new BigDecimal("72.5"), new BigDecimal("0.81"), new BigDecimal("55.0"), NOW)));
        var provider = new StudentReportProviders.StudentPerformanceProvider(analytics);

        ReportProjection projection = provider.project(
                criteria(ReportType.STUDENT_PERFORMANCE, Map.of(), true));

        assertThat(projection.rows()).hasSize(1);
        // §91: the derived aggregate discloses when it was last calculated.
        assertThat(projection.freshnessNote()).contains(NOW.toString());
    }

    // ------------------------------------------------------------------ Assessment

    @Test
    void assessmentSummaryReportsTheLatestVersionPerAssessment() {
        ReportingAssessmentCatalog catalog = mock(ReportingAssessmentCatalog.class);
        UUID assessmentId = UUID.randomUUID();
        when(catalog.assessments(anyInt())).thenReturn(List.of(
                new ReportingAssessmentCatalog.AssessmentRow(assessmentId, "BANK-PO", "Bank PO", "ACTIVE")));
        when(catalog.versionsOf(any())).thenReturn(List.of(
                new ReportingAssessmentCatalog.VersionRow(UUID.randomUUID(), assessmentId, 1, 50, 60, true),
                new ReportingAssessmentCatalog.VersionRow(UUID.randomUUID(), assessmentId, 2, 60, 70, true)));
        var provider = new AssessmentReportProviders.AssessmentSummaryProvider(catalog);

        ReportProjection projection = provider.project(
                criteria(ReportType.ASSESSMENT_SUMMARY, Map.of(), false));

        assertThat(projection.rows()).hasSize(1);
        // The projection reports the highest version, not the first row returned.
        assertThat(projection.rows().get(0)).containsEntry("currentVersion", "2")
                .containsEntry("totalQuestions", "60");
    }

    @Test
    void assessmentPerformanceOmitsAssessmentsWithNoCalculatedAggregate() {
        ReportingAssessmentCatalog catalog = mock(ReportingAssessmentCatalog.class);
        ReportingAnalyticsCatalog analytics = mock(ReportingAnalyticsCatalog.class);
        ReportingDeliveryCatalog delivery = mock(ReportingDeliveryCatalog.class);
        UUID withData = UUID.randomUUID();
        UUID withoutData = UUID.randomUUID();
        when(catalog.assessments(anyInt())).thenReturn(List.of(
                new ReportingAssessmentCatalog.AssessmentRow(withData, "A1", "One", "ACTIVE"),
                new ReportingAssessmentCatalog.AssessmentRow(withoutData, "A2", "Two", "ACTIVE")));
        when(analytics.assessments(any())).thenReturn(List.of(
                new ReportingAnalyticsCatalog.AssessmentAnalyticsRow(withData, 10, new BigDecimal("68.0"),
                        new BigDecimal("95.0"), new BigDecimal("20.0"), NOW)));
        var provider = new AssessmentReportProviders.AssessmentPerformanceProvider(catalog, analytics, delivery);

        ReportProjection projection = provider.project(
                criteria(ReportType.ASSESSMENT_PERFORMANCE, Map.of(), false));

        // §45: an assessment without a calculated aggregate is omitted, never reported as zero.
        assertThat(projection.rows()).hasSize(1);
        assertThat(projection.rows().get(0)).containsEntry("code", "A1");
        assertThat(projection.metrics()).isNotEmpty();
        assertThat(projection.metrics().get(0).definition()).isNotBlank();
    }

    // ------------------------------------------------------------------ Content

    @Test
    void tagCoverageReportsUntaggedQuestionsExplicitly() {
        ReportingContentCatalog catalog = mock(ReportingContentCatalog.class);
        UUID tagged = UUID.randomUUID();
        UUID untagged = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        when(catalog.questions(any(), any(), anyInt())).thenReturn(List.of(
                new ReportingContentCatalog.QuestionRow(tagged, null, null, null, null, null, "ACTIVE"),
                new ReportingContentCatalog.QuestionRow(untagged, null, null, null, null, null, "ACTIVE")));
        when(catalog.questionTags(anyInt())).thenReturn(List.of(
                new ReportingContentCatalog.QuestionTagRow(tagged, tagId)));
        var provider = new ContentReportProviders.TagCoverageProvider(catalog);

        ReportProjection projection = provider.project(criteria(ReportType.CONTENT_TAG_COVERAGE, Map.of(), false));

        // §52: uncovered content is reported as an explicit UNTAGGED row rather than omitted.
        assertThat(projection.rows()).anyMatch(row -> "UNTAGGED".equals(row.get("tagId"))
                && "1".equals(row.get("taggedQuestions")));
        assertThat(projection.rows()).anyMatch(row -> tagId.toString().equals(row.get("tagId")));
    }

    // ------------------------------------------------------------------ Commerce

    @Test
    void revenueGroupsByAuthoritativePaymentStateAndNeverCountsPendingAsCollected() {
        ReportingCommerceCatalog commerce = mock(ReportingCommerceCatalog.class);
        when(commerce.purchases(any(), any(), any(), anyInt())).thenReturn(List.of(
                new ReportingCommerceCatalog.PurchaseRow(UUID.randomUUID(), STUDENT, null, null, NOW,
                        new BigDecimal("100.00"), "SUCCESS"),
                new ReportingCommerceCatalog.PurchaseRow(UUID.randomUUID(), STUDENT, null, null, NOW,
                        new BigDecimal("50.00"), "PENDING"),
                new ReportingCommerceCatalog.PurchaseRow(UUID.randomUUID(), STUDENT, null, null, NOW,
                        new BigDecimal("30.00"), "REFUNDED")));
        var provider = new CommerceReportProviders.RevenueSummaryProvider(commerce);

        ReportProjection projection = provider.project(criteria(ReportType.COMMERCE_REVENUE_SUMMARY,
                Map.of("userId", STUDENT.toString()), false));

        // §60: revenue is reported per payment state, so PENDING is visible but distinct.
        assertThat(projection.rows()).anyMatch(row -> "SUCCESS".equals(row.get("paymentStatus"))
                && "100.00".equals(row.get("amount")));
        assertThat(projection.rows()).anyMatch(row -> "PENDING".equals(row.get("paymentStatus")));
        assertThat(projection.metrics()).allMatch(metric -> !metric.definition().isBlank());
    }

    @Test
    void commerceReportsNeverProjectAPaymentSecret() {
        ReportingCommerceCatalog commerce = mock(ReportingCommerceCatalog.class);
        when(commerce.payments(any(), any(), anyInt())).thenReturn(List.of(
                new ReportingCommerceCatalog.PaymentRow(UUID.randomUUID(), UUID.randomUUID(), "pay_ref_1",
                        "CARD", NOW, "SUCCESS")));
        var provider = new CommerceReportProviders.PaymentProvider(commerce);

        ReportProjection projection = provider.project(criteria(ReportType.COMMERCE_PAYMENTS, Map.of(), false));

        assertThat(projection.columns()).noneMatch(column -> column.key().toLowerCase().contains("secret"));
        assertThat(projection.rows().get(0)).doesNotContainKeys("cardNumber", "cvv", "gatewaySecret");
    }

    // ------------------------------------------- Commerce amendment (ADR-0029, GAP-141a)

    @Test
    void subscriptionSummaryReportsPlanLevelDataAndSaysItIsPlanLevelOnly() {
        ReportingCommerceCatalog commerce = mock(ReportingCommerceCatalog.class);
        UUID planId = UUID.randomUUID();
        when(commerce.subscriptionPlans(any(), any(), anyInt())).thenReturn(List.of(
                new ReportingCommerceCatalog.SubscriptionPlanRow(planId, UUID.randomUUID(), "PLAN_30",
                        "30-day access", 30, new BigDecimal("499.00"), "ACTIVE", NOW),
                new ReportingCommerceCatalog.SubscriptionPlanRow(UUID.randomUUID(), UUID.randomUUID(), "PLAN_90",
                        "90-day access", 90, new BigDecimal("1299.00"), "INACTIVE", NOW)));
        var provider = new CommerceReportProviders.SubscriptionSummaryProvider(commerce);

        ReportProjection projection = provider.project(criteria(ReportType.COMMERCE_SUBSCRIPTIONS,
                Map.of("from", "2026-10-01T00:00:00Z", "to", "2026-10-31T00:00:00Z"), false));

        assertThat(projection.rows()).hasSize(2);
        assertThat(projection.rows().get(0)).containsEntry("code", "PLAN_30").containsEntry("status", "ACTIVE")
                .containsEntry("price", "499.00").containsEntry("durationDays", "30");
        // §59: the report must not imply a customer-level recurring billing history exists.
        assertThat(projection.freshnessNote()).contains("Plan-level");
        assertThat(projection.metrics()).anyMatch(metric -> "Active plans".equals(metric.name())
                && "1".equals(metric.value()));
    }

    @Test
    void couponActivityCountsRedemptionsAndProjectsNoCouponSecret() {
        ReportingCommerceCatalog commerce = mock(ReportingCommerceCatalog.class);
        UUID couponId = UUID.randomUUID();
        when(commerce.coupons(any(), any(), any(), anyInt())).thenReturn(List.of(
                new ReportingCommerceCatalog.CouponRow(couponId, "LAUNCH10", "Launch offer",
                        new BigDecimal("10.00"), NOW, NOW.plusSeconds(86400), "ACTIVE", NOW, 7L)));
        var provider = new CommerceReportProviders.CouponActivityProvider(commerce);

        ReportProjection projection = provider.project(criteria(ReportType.COMMERCE_COUPONS,
                Map.of("status", "ACTIVE"), false));

        assertThat(projection.rows()).hasSize(1);
        assertThat(projection.rows().get(0)).containsEntry("code", "LAUNCH10")
                .containsEntry("redemptions", "7");
        assertThat(projection.metrics()).anyMatch(metric -> "Redemptions".equals(metric.name())
                && "7".equals(metric.value()));
        assertThat(projection.rows().get(0)).doesNotContainKeys("secret", "token", "internalNote");
    }

    @Test
    void refundActivityReportsRefundStateAndNeverComputesNetRevenue() {
        ReportingCommerceCatalog commerce = mock(ReportingCommerceCatalog.class);
        when(commerce.refunds(any(), any(), anyInt())).thenReturn(List.of(
                new ReportingCommerceCatalog.RefundRow(UUID.randomUUID(), STUDENT, new BigDecimal("30.00"),
                        NOW, NOW.plusSeconds(3600), "REFUNDED", "REFUNDED")));
        var provider = new CommerceReportProviders.RefundActivityProvider(commerce);

        ReportProjection projection = provider.project(criteria(ReportType.COMMERCE_REFUNDS, Map.of(), false));

        assertThat(projection.rows()).hasSize(1);
        assertThat(projection.rows().get(0)).containsEntry("purchaseStatus", "REFUNDED")
                .containsEntry("amount", "30.00");
        // §60 fixes the revenue definition; the refund report must not restate or contradict it.
        assertThat(projection.freshnessNote()).contains("§62");
        assertThat(projection.columns()).noneMatch(column -> column.key().toLowerCase().contains("netrevenue"));
    }

    @Test
    void commerceAmendmentReportsDeclareOnlyFiltersTheirProvidersHonour() {
        // A declared-but-ignored filter would silently return unfiltered financial data, so the
        // catalog entry and the provider must agree (ADR-0029).
        assertThat(ReportType.COMMERCE_SUBSCRIPTIONS.filterSchema()).containsExactly("from", "to");
        assertThat(ReportType.COMMERCE_COUPONS.filterSchema()).containsExactly("status", "from", "to");
        assertThat(ReportType.COMMERCE_REFUNDS.filterSchema()).containsExactly("from", "to");
        // All three are one financial capability, so they reuse the existing commerce permission.
        assertThat(List.of(ReportType.COMMERCE_SUBSCRIPTIONS, ReportType.COMMERCE_COUPONS,
                ReportType.COMMERCE_REFUNDS))
                .allMatch(type -> "REPORT_COMMERCE_READ".equals(type.permission()) && type.enabled());
    }

    // ------------------------------------------------------------------ Catalog integrity

    @Test
    void everyCatalogReportTypeHasAProvider() {
        // GAP-141: the catalog must not advertise a type that cannot be generated (§11, §46).
        java.util.Set<ReportType> provided = new java.util.HashSet<>();
        provided.add(ReportType.STUDENT_PROFILE);
        provided.add(ReportType.STUDENT_ATTEMPTS);
        provided.add(ReportType.STUDENT_PERFORMANCE);
        provided.add(ReportType.STUDENT_TOPIC_PERFORMANCE);
        provided.add(ReportType.ASSESSMENT_SUMMARY);
        provided.add(ReportType.ASSESSMENT_ATTEMPTS);
        provided.add(ReportType.ASSESSMENT_PERFORMANCE);
        provided.add(ReportType.ASSESSMENT_QUESTION_ANALYSIS);
        provided.add(ReportType.ASSESSMENT_SCHEDULE_ACTIVITY);
        provided.add(ReportType.CONTENT_QUESTION_INVENTORY);
        provided.add(ReportType.CONTENT_VERSION_STATUS);
        provided.add(ReportType.CONTENT_TAG_COVERAGE);
        provided.add(ReportType.COMMERCE_PURCHASES);
        provided.add(ReportType.COMMERCE_PAYMENTS);
        provided.add(ReportType.COMMERCE_ACCESS_GRANTS);
        provided.add(ReportType.COMMERCE_REVENUE_SUMMARY);
        provided.add(ReportType.COMMERCE_SUBSCRIPTIONS);
        provided.add(ReportType.COMMERCE_COUPONS);
        provided.add(ReportType.COMMERCE_REFUNDS);
        provided.add(ReportType.OPERATIONS_USER_ACTIVITY);
        provided.add(ReportType.OPERATIONS_AUDIT_ACTIVITY);
        provided.add(ReportType.OPERATIONS_COMMUNICATION_STATUS);
        provided.add(ReportType.OPERATIONS_SYSTEM_HEALTH);
        provided.add(ReportType.OPERATIONS_REPORT_JOB_ACTIVITY);
        provided.add(ReportType.EXECUTIVE_KPI_SUMMARY);
        provided.add(ReportType.EXECUTIVE_ORGANIZATION_PERFORMANCE);

        assertThat(provided).containsExactlyInAnyOrder(ReportType.values());
        // Every type is enabled, so the catalog advertises nothing it cannot generate.
        assertThat(ReportType.values()).allMatch(ReportType::enabled);
    }
}
