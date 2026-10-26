import Link from "next/link";
import {
  BarChart3,
  BookOpen,
  Building2,
  ClipboardCheck,
  FileText,
  GraduationCap,
  Lock,
  ShoppingCart,
  Timer,
  Users,
} from "lucide-react";

import { PublicLayout } from "@/components/public/public-layout";
import { ContactForm } from "@/features/public/contact-form";
import { Faq } from "@/features/public/faq";

/**
 * The public landing page ({@code docs/frontend/public-landing.md}, {@code specs/Design-Theme.md}).
 *
 * <p>Every claim here maps to a capability that exists in this build; nothing is aspirational, and
 * there are no testimonials, logos or statistics because the platform has no customers to quote.
 * The two calls to action are real routes: {@code /login} and the portal chooser beneath it.
 *
 * <p>Flat surfaces and a single accent colour only — {@code specs/Design-Theme.md} §21 forbids
 * decorative gradients.
 */

const CAPABILITIES = [
  {
    icon: ClipboardCheck,
    title: "Configurable examination engine",
    body: "Blueprints, selection rules, schedules, and timed delivery. Three examination patterns ship as first-class formats: NTA, TCS iON, and IBPS-style.",
  },
  {
    icon: BookOpen,
    title: "Question bank with LaTeX",
    body: "Author in MARKDOWN_LATEX_V1 with \\( \\) and \\[ \\] delimiters. Mathematics and science render correctly for both authors and candidates.",
  },
  {
    icon: Timer,
    title: "Server-authoritative delivery",
    body: "Timing, deadlines, and auto-submission are enforced by the backend. An answer saved offline is queued and reconciled, never trusted blindly.",
  },
  {
    icon: BarChart3,
    title: "Analytics that explain outcomes",
    body: "Student, assessment, question, topic, and organization insight, built from evaluated attempts rather than estimates.",
  },
  {
    icon: ShoppingCart,
    title: "Commerce and access control",
    body: "Access packages, purchases, and entitlement checks. Buying never unlocks content on the client, and every grant is server-verified.",
  },
  {
    icon: FileText,
    title: "Reporting with audit",
    body: "Twenty-three report types over CSV and PDF, with protected downloads and a transactional audit trail behind every mutation.",
  },
] as const;

const WORKFLOW = [
  ["Author", "Staff and reviewers write questions and assemble blueprints and assessments."],
  ["Schedule", "Administrators publish an assessment against a defined selection rule."],
  ["Deliver", "The runtime serves a timed attempt, with offline queueing and multi-tab protection."],
  ["Evaluate", "Answers are scored from the answer key and results are published to the candidate."],
] as const;

const PORTALS = [
  {
    href: "/login/student",
    icon: GraduationCap,
    title: "Student",
    body: "Take an assessment, review results and scorecards, track progress, and manage purchases.",
  },
  {
    href: "/login/organization",
    icon: Building2,
    title: "Organization staff",
    body: "Administer users and roles, author content, schedule assessments, and read reports and analytics.",
  },
  {
    href: "/login/admin",
    icon: Users,
    title: "System administration",
    body: "Platform-level operation: organizations, seeded reference data, provider configuration, and system health.",
  },
] as const;

export default function LandingPage() {
  return (
    <PublicLayout>
      {/* 1. Hero */}
      <section className="border-b border-border bg-surface">
        <div className="mx-auto flex max-w-4xl flex-col items-center gap-6 px-6 py-20 text-center sm:py-24">
          <p className="rounded-full border border-border bg-muted px-3 py-1 text-xs font-medium text-muted-foreground">
            Competitive examination platform
          </p>
          <h1 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl md:text-5xl">
            Run secure, large-scale examinations end to end.
          </h1>
          <p className="max-w-2xl text-base text-muted-foreground sm:text-lg">
            Author, schedule, deliver, and evaluate competitive examinations with a configurable
            engine, a LaTeX question bank, analytics, commerce, and reporting — in one system, with
            server-authoritative integrity throughout.
          </p>
          <div className="flex w-full flex-col gap-3 sm:w-auto sm:flex-row">
            <Link
              href="/login"
              className="inline-flex h-11 items-center justify-center rounded-md bg-primary px-6 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary-hover"
            >
              Sign in
            </Link>
            <Link
              href="#capabilities"
              className="inline-flex h-11 items-center justify-center rounded-md border border-input bg-surface px-6 text-sm font-medium text-foreground transition-colors hover:bg-muted"
            >
              Explore the platform
            </Link>
          </div>
          <p className="text-xs text-muted-foreground">
            Accounts are issued by your organization. There is no public self-registration.
          </p>
        </div>
      </section>

      {/* 2. Capabilities */}
      <section id="capabilities" className="mx-auto max-w-6xl scroll-mt-20 px-6 py-16">
        <div className="mb-8 max-w-2xl space-y-2">
          <h2 className="text-2xl font-semibold tracking-tight text-foreground">What the platform does</h2>
          <p className="text-sm text-muted-foreground">
            A modular examination system that carries an institute from question authoring to a
            published, auditable result.
          </p>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {CAPABILITIES.map((capability) => (
            <div key={capability.title} className="rounded-lg border border-border bg-surface p-5">
              <capability.icon className="mb-3 h-5 w-5 text-primary" aria-hidden />
              <h3 className="mb-1 font-medium text-foreground">{capability.title}</h3>
              <p className="text-sm text-muted-foreground">{capability.body}</p>
            </div>
          ))}
        </div>
      </section>

      {/* 3. Portals — the real entry points, clearly separated from the public site */}
      <section id="portals" className="border-y border-border bg-muted/40 py-16">
        <div className="mx-auto max-w-6xl px-6">
          <div className="mb-8 max-w-2xl space-y-2">
            <h2 className="text-2xl font-semibold tracking-tight text-foreground">Choose your portal</h2>
            <p className="text-sm text-muted-foreground">
              The public site explains the product. Day-to-day work happens in a role-specific
              workspace, and each one shows only what that role is permitted to do.
            </p>
          </div>
          <div className="grid gap-4 md:grid-cols-3">
            {PORTALS.map((portal) => (
              <Link
                key={portal.href}
                href={portal.href}
                className="group flex flex-col gap-2 rounded-lg border border-border bg-surface p-6 transition-colors hover:border-primary/40 hover:bg-primary-subtle/40"
              >
                <portal.icon className="h-5 w-5 text-primary" aria-hidden />
                <span className="font-medium text-foreground">{portal.title}</span>
                <span className="text-sm text-muted-foreground">{portal.body}</span>
                <span className="mt-1 text-sm font-medium text-primary group-hover:underline">
                  Sign in →
                </span>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* 4. How it works */}
      <section id="how-it-works" className="mx-auto max-w-6xl scroll-mt-20 px-6 py-16">
        <h2 className="mb-8 text-2xl font-semibold tracking-tight text-foreground">How it works</h2>
        <ol className="grid gap-6 md:grid-cols-4">
          {WORKFLOW.map(([title, body], index) => (
            <li key={title} className="space-y-2">
              <p className="text-sm font-semibold text-muted-foreground">{String(index + 1).padStart(2, "0")}</p>
              <h3 className="font-medium text-foreground">{title}</h3>
              <p className="text-sm text-muted-foreground">{body}</p>
            </li>
          ))}
        </ol>
      </section>

      {/* 5. Integrity — the property the product is actually sold on */}
      <section className="border-y border-border bg-muted/40 py-16">
        <div className="mx-auto flex max-w-3xl flex-col items-center gap-4 px-6 text-center">
          <Lock className="h-6 w-6 text-primary" aria-hidden />
          <h2 className="text-2xl font-semibold tracking-tight text-foreground">Integrity is enforced, not promised</h2>
          <p className="text-sm text-muted-foreground">
            The backend owns timing, deadlines, auto-submission, and answer persistence. Published
            examination artefacts are immutable, correct answers are never sent to the browser during
            an active attempt, and every mutation is written to an audit log in the same transaction.
          </p>
        </div>
      </section>

      {/* 6. FAQ */}
      <section className="mx-auto max-w-3xl px-6 py-16">
        <h2 className="mb-6 text-2xl font-semibold tracking-tight text-foreground">Frequently asked questions</h2>
        <Faq />
      </section>

      {/* 7. Contact */}
      <section id="contact" className="mx-auto max-w-3xl scroll-mt-20 px-6 pb-20">
        <h2 className="mb-6 text-2xl font-semibold tracking-tight text-foreground">Talk to us</h2>
        <ContactForm />
      </section>
    </PublicLayout>
  );
}
