"use client";

import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { ButtonLink } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { describeAdminError, formatDateTime } from "@/components/admin/format";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { useAccessGrants } from "@/features/commerce/hooks/use-commerce";

/**
 * Student home ({@code docs/frontend/navigation.md} "Redirect after login").
 *
 * <p>The home answers one question for a student: what can I sit right now? An examination reaches a
 * student either because their organization granted it or because they purchased it, and both arrive
 * as an access grant — so this page lists the grants they hold rather than making them hunt through a
 * store catalogue to rediscover what they already own.
 *
 * <p>Everything shown comes from a real read scoped to the caller by the backend
 * ({@code specs/17-P8 §52}); there is no invented "recommended" content and no placeholder data.
 */
export default function StudentHomePage() {
  const grants = useAccessGrants();
  const active = (grants.data ?? []).filter((grant) => grant.active);

  return (
    <div className="flex w-full flex-col gap-6">
      <PageHeader
        eyebrow="Student"
        title="Your examinations"
        description="Assessments assigned to you by your organization or purchased by you."
        actions={<ButtonLink href="/student/store">Explore assessments</ButtonLink>}
      />

      <AuthenticatedGuard>
        {grants.isPending ? (
          <p role="status" className="text-sm text-muted-foreground">Loading your examinations…</p>
        ) : null}

        {grants.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {describeAdminError(grants.error, "Your examinations could not be loaded.")}
          </p>
        ) : null}

        {grants.isSuccess && active.length === 0 ? (
          <EmptyState
            title="No examinations yet"
            description="When your organization assigns an assessment to you, or you purchase one, it will appear here."
            action={<ButtonLink href="/student/store">Explore assessments</ButtonLink>}
          />
        ) : null}

        {active.length > 0 ? (
          <section aria-labelledby="my-exams" className="space-y-3">
            <h2 id="my-exams" className="text-base font-semibold">
              Available to you
            </h2>
            <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {active.map((grant) => (
                <li key={grant.id}>
                  <Card className="h-full">
                    <CardHeader>
                      <div className="flex items-start justify-between gap-2">
                        <CardTitle className="text-base">{grant.packageName}</CardTitle>
                        <Badge variant="success">Active</Badge>
                      </div>
                    </CardHeader>
                    <CardContent className="space-y-3 text-sm">
                      <p className="text-muted-foreground">
                        {grant.endDate
                          ? "Access until " + (formatDateTime(grant.endDate) ?? grant.endDate)
                          : "No end date"}
                      </p>
                      <ButtonLink variant="outline" href="/student/access">
                        View access
                      </ButtonLink>
                    </CardContent>
                  </Card>
                </li>
              ))}
            </ul>
          </section>
        ) : null}

        <section aria-labelledby="quick-links" className="space-y-3">
          <h2 id="quick-links" className="text-base font-semibold">Your progress</h2>
          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {[
              { href: "/results", label: "Results", description: "Published scorecards." },
              { href: "/analytics/student", label: "Analytics", description: "Accuracy and progress." },
              { href: "/student/purchases", label: "Purchases", description: "Orders and receipts." },
              { href: "/account", label: "Account", description: "Your profile and password." },
            ].map((item) => (
              <li key={item.href}>
                <Link
                  href={item.href}
                  className="flex h-full flex-col gap-1 rounded-lg border border-border bg-surface p-4 transition-colors hover:border-primary/40 hover:bg-muted"
                >
                  <span className="font-medium text-foreground">{item.label}</span>
                  <span className="text-sm text-muted-foreground">{item.description}</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      </AuthenticatedGuard>
    </div>
  );
}
