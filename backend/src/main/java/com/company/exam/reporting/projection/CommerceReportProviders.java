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
}
