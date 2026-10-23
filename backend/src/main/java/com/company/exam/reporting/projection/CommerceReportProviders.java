package com.company.exam.reporting.projection;

import static com.company.exam.reporting.projection.Projections.MAX_ROWS;
import static com.company.exam.reporting.projection.Projections.appliedFilters;
import static com.company.exam.reporting.projection.Projections.columns;
import static com.company.exam.reporting.projection.Projections.decimal;
import static com.company.exam.reporting.projection.Projections.period;
import static com.company.exam.reporting.projection.Projections.row;
import static com.company.exam.reporting.projection.Projections.text;

import com.company.exam.commerce.api.ReportingCommerceCatalog;
import com.company.exam.reporting.catalog.ReportType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Commerce report projections ({@code specs/22-P13-Reporting-V1.5.md §55-§62}).
 *
 * <p>Purchases, payments, access grants and coupon activity are read through the Commerce contract
 * ({@code §10}). Reporting never changes a payment, issues a refund, or grants access ({@code §7.3}),
 * and no provider credential or gateway secret is projected ({@code §82}). A student caller is scoped
 * to their own user id before the read ({@code §76}).
 */
public final class CommerceReportProviders {

    private CommerceReportProviders() {
    }

    /** COMMERCE_PURCHASES — purchase rows in a period (§56). */
    @Component
    public static class PurchaseProvider implements ReportDataProvider {

        private final ReportingCommerceCatalog commerceCatalog;

        public PurchaseProvider(ReportingCommerceCatalog commerceCatalog) {
            this.commerceCatalog = commerceCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.COMMERCE_PURCHASES;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            var purchases = commerceCatalog.purchases(criteria.scopedSubject("userId"), criteria.instant("from"),
                    criteria.instant("to"), MAX_ROWS);
            if (purchases.isEmpty()) {
                return Projections.empty(ReportType.COMMERCE_PURCHASES.displayName(), criteria);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (ReportingCommerceCatalog.PurchaseRow purchase : purchases) {
                if (purchase.amount() != null) {
                    total = total.add(purchase.amount());
                }
                rows.add(row(
                        "purchaseId", text(purchase.purchaseId()),
                        "userId", text(purchase.userId()),
                        "subscriptionPlanId", text(purchase.subscriptionPlanId()),
                        "couponId", text(purchase.couponId()),
                        "purchaseDate", text(purchase.purchaseDate()),
                        "amount", decimal(purchase.amount()),
                        "paymentStatus", purchase.paymentStatus()));
            }
            return new ReportProjection(ReportType.COMMERCE_PURCHASES.displayName(), period(criteria.filters()),
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("purchaseId", "Purchase id", "userId", "User", "subscriptionPlanId", "Plan",
                            "couponId", "Coupon", "purchaseDate", "Purchased", "amount", "Amount",
                            "paymentStatus", "Payment status"),
                    rows, List.of(new ReportProjection.SummaryMetric("Purchases", Long.toString(purchases.size()),
                            "Number of purchases in the requested window"),
                            new ReportProjection.SummaryMetric("Purchase value", decimal(total),
                                    "Sum of purchase amounts in the requested window")),
                    "Purchase records only; Reporting never changes a payment state.");
        }
    }

    /** COMMERCE_PAYMENTS — payment rows in a period (§57). */
    @Component
    public static class PaymentProvider implements ReportDataProvider {

        private final ReportingCommerceCatalog commerceCatalog;

        public PaymentProvider(ReportingCommerceCatalog commerceCatalog) {
            this.commerceCatalog = commerceCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.COMMERCE_PAYMENTS;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            var payments = commerceCatalog.payments(criteria.instant("from"), criteria.instant("to"), MAX_ROWS);
            if (payments.isEmpty()) {
                return Projections.empty(ReportType.COMMERCE_PAYMENTS.displayName(), criteria);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (ReportingCommerceCatalog.PaymentRow payment : payments) {
                rows.add(row(
                        "paymentId", text(payment.paymentId()),
                        "purchaseId", text(payment.purchaseId()),
                        "paymentReference", payment.paymentReference(),
                        "paymentMethod", payment.paymentMethod(),
                        "paymentDate", text(payment.paymentDate()),
                        "paymentStatus", payment.paymentStatus()));
            }
            return new ReportProjection(ReportType.COMMERCE_PAYMENTS.displayName(), period(criteria.filters()),
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("paymentId", "Payment id", "purchaseId", "Purchase", "paymentReference", "Reference",
                            "paymentMethod", "Method", "paymentDate", "Paid", "paymentStatus", "Status"),
                    rows, List.of(),
                    "Payment records only; no provider credential or gateway payload is included (§82).");
        }
    }

    /** COMMERCE_ACCESS_GRANTS — grants started in a period (§58). */
    @Component
    public static class AccessGrantProvider implements ReportDataProvider {

        private final ReportingCommerceCatalog commerceCatalog;

        public AccessGrantProvider(ReportingCommerceCatalog commerceCatalog) {
            this.commerceCatalog = commerceCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.COMMERCE_ACCESS_GRANTS;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            var grants = commerceCatalog.accessGrants(criteria.scopedSubject("userId"), criteria.instant("from"),
                    criteria.instant("to"), MAX_ROWS);
            if (grants.isEmpty()) {
                return Projections.empty(ReportType.COMMERCE_ACCESS_GRANTS.displayName(), criteria);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (ReportingCommerceCatalog.AccessGrantRow grant : grants) {
                rows.add(row(
                        "grantId", text(grant.grantId()),
                        "userId", text(grant.userId()),
                        "accessPackageId", text(grant.accessPackageId()),
                        "startDate", text(grant.startDate()),
                        "endDate", text(grant.endDate()),
                        "status", grant.status()));
            }
            return new ReportProjection(ReportType.COMMERCE_ACCESS_GRANTS.displayName(), period(criteria.filters()),
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("grantId", "Grant id", "userId", "User", "accessPackageId", "Package",
                            "startDate", "Start", "endDate", "End", "status", "Status"),
                    rows, List.of(),
                    "Access-grant records only; Reporting never creates or revokes a grant (§7.3).");
        }
    }

    /** COMMERCE_REVENUE_SUMMARY — revenue in a period (§60). */
    @Component
    public static class RevenueSummaryProvider implements ReportDataProvider {

        private final ReportingCommerceCatalog commerceCatalog;

        public RevenueSummaryProvider(ReportingCommerceCatalog commerceCatalog) {
            this.commerceCatalog = commerceCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.COMMERCE_REVENUE_SUMMARY;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            var purchases = commerceCatalog.purchases(criteria.scopedSubject("userId"), criteria.instant("from"),
                    criteria.instant("to"), MAX_ROWS);
            if (purchases.isEmpty()) {
                return Projections.empty(ReportType.COMMERCE_REVENUE_SUMMARY.displayName(), criteria);
            }
            // Revenue is grouped by payment state, so a pending purchase is never counted as collected.
            Map<String, BigDecimal> byStatus = new java.util.LinkedHashMap<>();
            Map<String, Long> countByStatus = new java.util.LinkedHashMap<>();
            for (ReportingCommerceCatalog.PurchaseRow purchase : purchases) {
                String status = purchase.paymentStatus() == null ? "UNKNOWN" : purchase.paymentStatus();
                BigDecimal amount = purchase.amount() == null ? BigDecimal.ZERO : purchase.amount();
                byStatus.merge(status, amount, BigDecimal::add);
                countByStatus.merge(status, 1L, Long::sum);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            List<ReportProjection.SummaryMetric> metrics = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> entry : byStatus.entrySet()) {
                rows.add(row("paymentStatus", entry.getKey(),
                        "purchases", countByStatus.get(entry.getKey()),
                        "amount", decimal(entry.getValue())));
                metrics.add(new ReportProjection.SummaryMetric("Amount · " + entry.getKey(),
                        decimal(entry.getValue()),
                        "Sum of purchase amounts whose payment_status is " + entry.getKey()));
            }
            return new ReportProjection(ReportType.COMMERCE_REVENUE_SUMMARY.displayName(), period(criteria.filters()),
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("paymentStatus", "Payment status", "purchases", "Purchases", "amount", "Amount"),
                    rows, metrics,
                    "Revenue is grouped by authoritative payment state; a pending purchase is not counted "
                            + "as collected (§60).");
        }
    }

    /**
     * COMMERCE_SUBSCRIPTIONS — the subscription-plan catalogue (§59, ADR-0029).
     *
     * <p>Plan level only. §59 forbids inventing a customer-level recurring billing history, and the
     * platform has no subscription entity: a purchase of a plan is recorded as an {@code access_grant}.
     * The report therefore describes what plans exist and what they cost, and says so.
     */
    @Component
    public static class SubscriptionSummaryProvider implements ReportDataProvider {

        private final ReportingCommerceCatalog commerceCatalog;

        public SubscriptionSummaryProvider(ReportingCommerceCatalog commerceCatalog) {
            this.commerceCatalog = commerceCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.COMMERCE_SUBSCRIPTIONS;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            var plans = commerceCatalog.subscriptionPlans(criteria.instant("from"), criteria.instant("to"),
                    MAX_ROWS);
            if (plans.isEmpty()) {
                return Projections.empty(ReportType.COMMERCE_SUBSCRIPTIONS.displayName(), criteria);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            long active = 0;
            for (ReportingCommerceCatalog.SubscriptionPlanRow plan : plans) {
                if ("ACTIVE".equals(plan.status())) {
                    active++;
                }
                rows.add(row(                        "planId", text(plan.planId()),
                        "code", plan.code(),
                        "name", plan.name(),
                        "accessPackageId", text(plan.accessPackageId()),
                        "durationDays", plan.durationDays() == null ? "" : String.valueOf(plan.durationDays()),
                        "price", decimal(plan.price()),
                        "status", plan.status(),
                        "createdOn", text(plan.createdOn())));
            }
            return new ReportProjection(ReportType.COMMERCE_SUBSCRIPTIONS.displayName(), period(criteria.filters()),
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("planId", "Plan id", "code", "Code", "name", "Name",
                            "accessPackageId", "Access package", "durationDays", "Duration (days)",
                            "price", "Price", "status", "Status", "createdOn", "Created"),
                    rows, List.of(
                            new ReportProjection.SummaryMetric("Plans", Integer.toString(plans.size()),
                                    "Subscription plans created in the period"),
                            new ReportProjection.SummaryMetric("Active plans", Long.toString(active),
                                    "Reported plans currently in ACTIVE state")),
                    "Plan-level summary only. The platform records purchased access as an access grant and "
                            + "keeps no recurring subscription history (§59).");
        }
    }

    /**
     * COMMERCE_COUPONS — coupon activity with redemption counts (§61, ADR-0029).
     *
     * <p>No coupon secret is projected: the discount value and validity window are the reportable
     * facts, and §61 requires usage count, status and validity window.
     */
    @Component
    public static class CouponActivityProvider implements ReportDataProvider {

        private final ReportingCommerceCatalog commerceCatalog;

        public CouponActivityProvider(ReportingCommerceCatalog commerceCatalog) {
            this.commerceCatalog = commerceCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.COMMERCE_COUPONS;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            var coupons = commerceCatalog.coupons(criteria.string("status"), criteria.instant("from"),
                    criteria.instant("to"), MAX_ROWS);
            if (coupons.isEmpty()) {
                return Projections.empty(ReportType.COMMERCE_COUPONS.displayName(), criteria);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            long redemptions = 0;
            for (ReportingCommerceCatalog.CouponRow coupon : coupons) {
                redemptions += coupon.redemptionCount();
                rows.add(row(
                        "couponId", text(coupon.couponId()),
                        "code", coupon.code(),
                        "name", coupon.name(),
                        "discountValue", decimal(coupon.discountValue()),
                        "validFrom", text(coupon.validFrom()),
                        "validTo", text(coupon.validTo()),
                        "status", coupon.status(),
                        "redemptions", Long.toString(coupon.redemptionCount()),
                        "createdOn", text(coupon.createdOn())));
            }
            return new ReportProjection(ReportType.COMMERCE_COUPONS.displayName(), period(criteria.filters()),
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("couponId", "Coupon id", "code", "Code", "name", "Name",
                            "discountValue", "Discount", "validFrom", "Valid from", "validTo", "Valid to",
                            "status", "Status", "redemptions", "Redemptions", "createdOn", "Created"),
                    rows, List.of(
                            new ReportProjection.SummaryMetric("Coupons", Integer.toString(coupons.size()),
                                    "Coupons created in the period"),
                            new ReportProjection.SummaryMetric("Redemptions", Long.toString(redemptions),
                                    "Purchases that reference a reported coupon")),
                    "Coupon usage is counted from purchases that reference the coupon; no coupon secret "
                            + "or administrative internal is projected (§61, §82).");
        }
    }

    /**
     * COMMERCE_REFUNDS — purchases and payments in refunded state (§62, ADR-0029).
     *
     * <p>Refund truth is the authoritative payment state on {@code purchase} / {@code payment_record}.
     * No refund entity exists in the schema and none is invented; no net-revenue figure is computed
     * here, because §60 fixes the accounting definition in the revenue report.
     */
    @Component
    public static class RefundActivityProvider implements ReportDataProvider {

        private final ReportingCommerceCatalog commerceCatalog;

        public RefundActivityProvider(ReportingCommerceCatalog commerceCatalog) {
            this.commerceCatalog = commerceCatalog;
        }

        @Override
        public ReportType reportType() {
            return ReportType.COMMERCE_REFUNDS;
        }

        @Override
        public ReportProjection project(ReportCriteria criteria) {
            var refunds = commerceCatalog.refunds(criteria.instant("from"), criteria.instant("to"), MAX_ROWS);
            if (refunds.isEmpty()) {
                return Projections.empty(ReportType.COMMERCE_REFUNDS.displayName(), criteria);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (ReportingCommerceCatalog.RefundRow refund : refunds) {
                if (refund.amount() != null) {
                    total = total.add(refund.amount());
                }
                rows.add(row(
                        "purchaseId", text(refund.purchaseId()),
                        "userId", text(refund.userId()),
                        "amount", decimal(refund.amount()),
                        "purchaseDate", text(refund.purchaseDate()),
                        "refundedOn", text(refund.refundedOn()),
                        "purchaseStatus", refund.purchasePaymentStatus(),
                        "paymentStatus", refund.paymentStatus()));
            }
            return new ReportProjection(ReportType.COMMERCE_REFUNDS.displayName(), period(criteria.filters()),
                    appliedFilters(criteria.filters()), criteria.generatedAt(), criteria.callerId(),
                    columns("purchaseId", "Purchase id", "userId", "User", "amount", "Amount",
                            "purchaseDate", "Purchased", "refundedOn", "Refunded (last state change)",
                            "purchaseStatus", "Purchase status", "paymentStatus", "Payment status"),
                    rows, List.of(new ReportProjection.SummaryMetric("Refunds", Integer.toString(refunds.size()),
                            "Purchases in refunded state in the period")),
                    "Refund state is sourced from the Commerce-owned payment contract (§62). There is no "
                            + "separate refund entity, so the refunded-on column is the last recorded state "
                            + "change. Communication failures never change refund truth.");
        }
    }
}
