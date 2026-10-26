"use client";

import Link from "next/link";

import { useAuth } from "@/lib/auth/auth-context";

/**
 * Analytics entry point ({@code specs/17-P8-Analytics-V1.5.md §142}).
 *
 * <p>Links are a UI affordance only: every destination is independently authorized by the
 * backend, so hiding a link is never the security boundary ({@code §148}).
 */
export default function AnalyticsPage() {
  const { can, status } = useAuth();
  const isStaff = can("ANALYTICS_VIEW");

  return (
    <div className="flex w-full flex-col gap-6">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Analytics</p>
        <h1 className="text-2xl font-semibold tracking-tight">Analytics and insights</h1>
        <p className="text-sm text-muted-foreground">
          Derived metrics, refreshed from evaluated attempts. Every view states what it measures,
          when it was calculated, and shows an empty state instead of a misleading zero.
        </p>
      </header>

      <nav aria-label="Analytics views" className="grid gap-4 sm:grid-cols-2">
        <Link
          href="/analytics/student"
          className="rounded-lg border border-border bg-surface p-4 shadow-sm hover:border-info focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          <h2 className="text-base font-semibold">My analytics</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Your attempts, average score, accuracy, topic performance and progress.
          </p>
        </Link>

        {status === "authenticated" && isStaff ? (
          <Link
            href="/analytics/organization"
            className="rounded-lg border border-border bg-surface p-4 shadow-sm hover:border-info focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
          >
            <h2 className="text-base font-semibold">Organization analytics</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Students, assessments, attempts and active users for your organization.
            </p>
          </Link>
        ) : null}
      </nav>

      <section aria-labelledby="analytics-staff-note" className="space-y-2">
        <h2 id="analytics-staff-note" className="text-base font-semibold">
          Assessment, question and topic dashboards
        </h2>
        <p className="text-sm text-muted-foreground">
          Staff with the analytics permission open these from an assessment, question or topic
          record. Their URLs are <code>/analytics/assessments/&lt;id&gt;</code>,{" "}
          <code>/analytics/questions/&lt;id&gt;</code> and <code>/analytics/topics/&lt;id&gt;</code>.
        </p>
      </section>
    </div>
  );
}
