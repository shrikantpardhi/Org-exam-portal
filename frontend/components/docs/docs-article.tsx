"use client";

import { useEffect, useState } from "react";

import { renderMarkdown, type DocHeading } from "@/lib/markdown";

/**
 * Renders a bundled docs page and its right-hand table of contents ({@code ADR-0036}). The TOC
 * lists level-2+ headings and highlights the section currently in view.
 */
export function DocsArticle({ markdown, headings }: { markdown: string; headings: DocHeading[] }) {
  const toc = headings.filter((heading) => heading.level >= 2);
  const [active, setActive] = useState<string | null>(toc[0]?.id ?? null);

  useEffect(() => {
    const targets = toc
      .map((heading) => document.getElementById(heading.id))
      .filter((node): node is HTMLElement => node !== null);
    if (targets.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
 if (visible[0]) setActive(visible[0].target.id);
 },
 { rootMargin: "-80px 0px -70% 0px" },
    );
    targets.forEach((target) => observer.observe(target));
    return () => observer.disconnect();
  }, [toc]);

  return (
    <div className="flex gap-8">
      <article className="min-w-0 flex-1">{renderMarkdown(markdown)}</article>
      {toc.length > 0 ? (
        <aside className="sticky top-24 hidden w-52 shrink-0 self-start lg:block">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">On this page</p>
          <nav className="space-y-1 border-l border-border">
            {toc.map((heading) => (
              <a
 key={heading.id}
 href={`#${heading.id}`}
 className={
 active === heading.id
 ? "block border-l-2 border-primary py-1 pl-3 text-xs font-medium text-foreground"
 : "block border-l-2 border-transparent py-1 pl-3 text-xs text-muted-foreground hover:text-foreground"
                }
              >
                {heading.text}
              </a>
            ))}
          </nav>
        </aside>
      ) : null}
    </div>
  );
}
