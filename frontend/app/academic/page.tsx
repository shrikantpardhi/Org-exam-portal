import Link from "next/link";

import { AcademicHierarchy } from "@/features/academic/components/academic-hierarchy";

const LINKS = [
  { href: "/academic/exams", label: "Exams" },
  { href: "/academic/subjects", label: "Subjects" },
  { href: "/academic/chapters", label: "Chapters" },
  { href: "/academic/topics", label: "Topics" },
  { href: "/academic/sub-topics", label: "Sub-topics" },
  { href: "/academic/languages", label: "Languages" },
  { href: "/academic/tags", label: "Tags" },
];

export default function AcademicPage() {
  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Academic structure</h1>
      <p className="text-sm text-slate-600">
        Exam to Subject to Chapter to Topic to SubTopic. Children load lazily as each level is expanded.
      </p>

      <nav aria-label="Academic sections" className="flex flex-wrap gap-3 text-sm">
        {LINKS.map((link) => (
          <Link key={link.href} href={link.href} className="rounded border border-slate-200 px-3 py-1 hover:bg-slate-50">
            {link.label}
          </Link>
        ))}
      </nav>

      <AcademicHierarchy />
    </main>
  );
}
