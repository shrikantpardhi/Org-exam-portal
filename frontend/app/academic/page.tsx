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
    <div className="flex w-full flex-col gap-6">
      <h1 className="text-2xl font-semibold tracking-tight">Academic structure</h1>
      <p className="text-sm text-muted-foreground">
        Exam to Subject to Chapter to Topic to SubTopic. Children load lazily as each level is expanded.
      </p>

      <nav aria-label="Academic sections" className="flex flex-wrap gap-3 text-sm">
        {LINKS.map((link) => (
          <Link key={link.href} href={link.href} className="rounded border border-border px-3 py-1 hover:bg-muted">
            {link.label}
          </Link>
        ))}
      </nav>

      <AcademicHierarchy />
    </div>
  );
}
